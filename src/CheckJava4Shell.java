/**
 * Sjekker CVE-2026-114938 (Java4Shell): https://calif.io/java4shell
 * Alle skrivinger havner i en egen 4096-byte array, så testen er trygg å kjøre.
 * Exit-kode 0 = ikke sårbar, 1 = sårbar (minne overskrevet).
 *
 * Kjøre:
 *      java --source 25 src/CheckJava4Shell.java
 *
 * For å få filen over på en kjørende tjeneste:
 * 1. Start en en debug container -- se README.md
 * 2. Opprett filen: echo '<paste innhold av CheckJava4Shell.java>' > /proc/1/root/tmp/CheckJava4Shell.java
 * 3. Kjør via exec på poden: kubectl exec $pod --tty --stdin -- java -Xbatch --source 25 /tmp/CheckJava4Shell.java
 */
void main() throws java.io.IOException {
    System.out.println("Sjekker Java4Shell-sårbarhet...");
    System.out.println("runtime=" + System.getProperty("java.runtime.version"));
    System.out.println("vm=" + System.getProperty("java.vm.name"));
    System.out.println("arch=" + System.getProperty("os.arch"));

    if (!vulnerableCodePathAvailable()) {
        System.out.println("==============================================================");
        System.out.println("CPU INFO: den sårbare stien i JVM vil ikke bli kjørt");
        System.out.println("(krever x86-64 med avx512f, avx512bw, avx512vbmi og bmi2, f.eks. Intel Ice Lake+ / AMD Zen 4+).");
        System.out.println("Prøver likevel, just in case");
        System.out.println("==============================================================");
    }

    var decoder = java.util.Base64.getDecoder();
    boolean vulnerable = false;

    // 64 tegn trigger i nyere JDK, 128 tegn i eldre OpenJDK 17
    for (int length : new int[] {64, 128}) {
        var valid = "A".repeat(length).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var malformed = ("A".repeat(length) + "=").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var target = new byte[4096];

        // Varm opp dekoderen slik at JIT (C2) velger den intrinsiske implementasjonen
        int sink = 0;
        for (int i = 0; i < 200_000; i++) {
            sink += decoder.decode(valid, target);
        }

        java.util.Arrays.fill(target, (byte) 0x5a);
        boolean rejected = false;
        try {
            decoder.decode(malformed, target);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }

        // Gyldig resultat er maks length/4*3 bytes; alt etter det skal være urørt
        int changed = 0;
        for (int i = length / 4 * 3; i < target.length; i++) {
            if (target[i] != (byte) 0x5a) {
                changed++;
            }
        }

        System.out.println("length=" + length + " rejected=" + rejected + " changed=" + changed + " (sink=" + sink + ")");
        vulnerable |= changed > 0;
    }

    System.out.println("==============================================================");
    if (vulnerable) {
        System.out.println("SÅRBAR: dekoderen overskrev minne utenfor resultatet");
        System.exit(1);
    } else {
        System.out.println("IKKE SÅRBAR: dekoderen overskrev ikke minne utenfor resultatet");
    }
    System.out.println("==============================================================");
}

// Den sårbare stien krever alle CPU-flaggene under og AVX-512 og Base64-intrinsics i JVM-en
boolean vulnerableCodePathAvailable() throws java.io.IOException {
    var flags = new java.util.HashSet<>(java.util.Arrays.asList(cpuFlags().trim().split("\\s+")));
    boolean cpuHasRequiredFlags = true;
    for (var flag : new String[] {"avx512f", "avx512bw", "avx512vbmi", "bmi2"}) {
        boolean available = flags.contains(flag);
        System.out.println("cpu " + flag + "=" + available);
        cpuHasRequiredFlags &= available;
    }
    try {
        var bean = java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
        int useAvx = Integer.parseInt(bean.getVMOption("UseAVX").getValue());
        boolean intrinsics = Boolean.parseBoolean(bean.getVMOption("UseBASE64Intrinsics").getValue());
        System.out.println("jvm UseAVX=" + useAvx + " UseBASE64Intrinsics=" + intrinsics);
        return cpuHasRequiredFlags && useAvx >= 3 && intrinsics;
    } catch (IllegalArgumentException e) {
        System.out.println("jvm: UseAVX/UseBASE64Intrinsics finnes ikke (ikke x86-64)");
        return false;
    }
}

// Linux (også i container): /proc/cpuinfo. macOS: sysctl. Alt i små bokstaver.
String cpuFlags() throws java.io.IOException {
    var cpuinfo = java.nio.file.Path.of("/proc/cpuinfo");
    if (java.nio.file.Files.exists(cpuinfo)) {
        return java.nio.file.Files.readAllLines(cpuinfo).stream()
                .filter(line -> line.startsWith("flags"))
                .findFirst().orElse("").toLowerCase();
    }
    if (System.getProperty("os.name").startsWith("Mac")) {
        var process = new ProcessBuilder("sysctl", "-n", "machdep.cpu.features", "machdep.cpu.leaf7_features")
                .redirectErrorStream(true).start();
        return new String(process.getInputStream().readAllBytes()).toLowerCase().replace("\n", " ");
    }
    return "";
}
