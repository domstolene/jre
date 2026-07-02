/**
 * Ettersom vi ikke har shell i base image, må vi trikse og bruke Java
 * for å slette verktøy som vi ikke trenger i image.
 */
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

void main() {
    String[] toolsToRemove = {
        // Oppstart
        "pebble",       // Pebble er et verktøy for å starte applikasjoner, som vi ikke trenger

        // Utvikling, kompilering og analyse
        "javac",        // Java-kompilatoren
        "jshell",       // Interaktivt Java-skall (REPL)
        "jlink",        // Verktøy for å lage tilpassede JREs
        "jmod",         // Håndterer Java-moduler
        "javadoc",      // Genererer dokumentasjon
        "jar",          // Pakkeverktøy for .jar-filer
        "jdeps",        // Analyserer pakkeavhengigheter (dependencies)
        "jnativescan",  // Scanner etter bruk av native funksjoner (nytt i JDK)
        "jdeprscan",    // Scanner kode for utgåtte (deprecated) API-er
        "javap",        // Disassembler klassefiler (viser bytecode)
        "jrunscript",   // Kommandolinjeverktøy for å kjøre JavaScript/skripting i Java

        // Feilsøking, profilering og overvåking (Svært attraktive for angripere)
        "jcmd",         // Sender kontrollkommandoer til en kjørende JVM
        "jdb",          // Java-debuggeren
        "jhsdb",        // Avansert "Serviceability Agent" debugger
        "jinfo",        // Genererer konfigurasjonsinformasjon fra en prosess
        "jmap",         // Dumper minnet (Memory/Heap dump)
        "jstack",       // Dumper alle tråder (Thread stack dump)
        "jstat",        // Overvåker ytelsesstatistikk
        "jstatd",       // RMI-server for jstat
        "jimage",       // Verktøy for å pakke ut/vis jimage-filer
        "jpackage",     // Pakker applikasjoner inn i native formater
        "jps",          // Lister ut kjørende Java-prosesser på systemet
        "jconsole",     // Grafisk verktøy for overvåking (krever uansett skjerm/X11)
        "jfr",          // Verktøy for å analysere Java Flight Recorder-filer i etterkant

        // Sikkerhet, nettverk og andre tjenester
        "jarsigner",    // Brukes til å signere JAR-filer (trengs ikke for å kjøre dem)
        "rmiregistry",  // Bootstrap-tjeneste for Legacy Java RMI (Remote Method Invocation)
        "jwebserver"    // Minimal innebygd webserver for statiske filer (introdusert i Java 18)
    };

    // java.home peker alltid til den kjørende JDK-installasjonen, uavhengig av versjon eller arkitektur
    Path jdkBin = Paths.get(System.getProperty("java.home"), "bin");
    System.out.println("JDK bin-mappe: " + jdkBin);
    System.out.println("Gjeldende PATH:\n" + System.getenv("PATH"));

    for (String tool : toolsToRemove) {
        // Slett den faktiske binærfilen i JDK-installasjonen
        File jdkFile = jdkBin.resolve(tool).toFile();
        if (jdkFile.exists()) {
            System.out.println("Deleting JDK binary " + jdkFile.getAbsolutePath() + ", result: " + jdkFile.delete());
        } else {
            System.out.println("Not found in JDK bin: " + jdkFile.getAbsolutePath());
        }

        // Rydd opp lenker på PATH som pekte til den nå-slettede binærfilen
        slettSymlenkerPaPath(tool);
    }

    // fjern alle pebble-relaterte filer i /var/lib/pebble
    var result = deleteRecursive(new File("/var/lib/pebble"));
    System.out.println("Deleting /var/lib/pebble, result: " + result);

    // Slett oss selv til slutt
    File me = new File("/app/RemoveTools.java");
    File meClass = new File("/app/RemoveTools.class");
    if (me.exists()) {
        System.out.println("Deleting self, result: " + me.delete());
    } else {
        System.out.println("Self file not found: " + me.getAbsolutePath());
    }
    if (meClass.exists()) {
        System.out.println("Deleting self class, result: " + meClass.delete());
    } else {
        System.out.println("Self class file not found: " + meClass.getAbsolutePath());
    }
}

/**
 * Sletter alle filer (inkl. symbollenker) med gitt navn fra kataloger i PATH.
 */
void slettSymlenkerPaPath(String programNavn) {
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null || pathEnv.isEmpty()) return;

    for (String mappe : pathEnv.split(":")) {
        Path fil = Paths.get(mappe, programNavn);
        // Files.exists følger lenker; bruk Files.lexists (finnes ikke i Java) —
        // vi sjekker derfor med toFile().exists() OG Links.isSymbolicLink
        if (Files.isSymbolicLink(fil) || fil.toFile().exists()) {
            System.out.println("Deleting PATH entry " + fil + ", result: " + fil.toFile().delete());
        }
    }
}

boolean deleteRecursive(File path) {
    if (path.isDirectory()) {
        File[] children = path.listFiles();
        if (children != null) {
            for (File child : children) { deleteRecursive(child); }
        }
    }
    return path.delete();
}
