import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

public class RemoveUntrustedCertificateRootsTest {
    private static final String EMSIGN_ECC_ROOT_G3_PEM = """
            -----BEGIN CERTIFICATE-----
            MIICTjCCAdOgAwIBAgIKPPYHqWhwDtqLhDAKBggqhkjOPQQDAzBrMQswCQYDVQQG
            EwJJTjETMBEGA1UECxMKZW1TaWduIFBLSTElMCMGA1UEChMcZU11ZGhyYSBUZWNo
            bm9sb2dpZXMgTGltaXRlZDEgMB4GA1UEAxMXZW1TaWduIEVDQyBSb290IENBIC0g
            RzMwHhcNMTgwMjE4MTgzMDAwWhcNNDMwMjE4MTgzMDAwWjBrMQswCQYDVQQGEwJJ
            TjETMBEGA1UECxMKZW1TaWduIFBLSTElMCMGA1UEChMcZU11ZGhyYSBUZWNobm9s
            b2dpZXMgTGltaXRlZDEgMB4GA1UEAxMXZW1TaWduIEVDQyBSb290IENBIC0gRzMw
            djAQBgcqhkjOPQIBBgUrgQQAIgNiAAQjpQy4LRL1KPOxst3iAhKAnjlfSU2fySU0
            WXTsuwYc58Byr+iuL+FBVIcUqEqy6HyC5ltqtdyzdc6LBtCGI79G1Y4PPwT01xyS
            fvalY8L1X44uT6EYGQIrMgqCZH0Wk9GjQjBAMB0GA1UdDgQWBBR8XQKEE9TMipuB
            zhccLikenEhjQjAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zAKBggq
            hkjOPQQDAwNpADBmAjEAvvNhzwIQHWSVB7gYboiFBS+DCBeQyh+KTOgNG3qxrdWB
            CUfvO6wIBHxcmbHtRwfSAjEAnbpV/KlK6O3t5nYBQnvI+GDZjVGLVTv7jHvrZQnD
            +JbNR6iC8hZVdyR+EhCVBCyj
            -----END CERTIFICATE-----
            """;

    private static final String AAA_CERTIFICATE_SERVICES_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIEMjCCAxqgAwIBAgIBATANBgkqhkiG9w0BAQUFADB7MQswCQYDVQQGEwJHQjEb
            MBkGA1UECAwSR3JlYXRlciBNYW5jaGVzdGVyMRAwDgYDVQQHDAdTYWxmb3JkMRow
            GAYDVQQKDBFDb21vZG8gQ0EgTGltaXRlZDEhMB8GA1UEAwwYQUFBIENlcnRpZmlj
            YXRlIFNlcnZpY2VzMB4XDTA0MDEwMTAwMDAwMFoXDTI4MTIzMTIzNTk1OVowezEL
            MAkGA1UEBhMCR0IxGzAZBgNVBAgMEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UE
            BwwHU2FsZm9yZDEaMBgGA1UECgwRQ29tb2RvIENBIExpbWl0ZWQxITAfBgNVBAMM
            GEFBQSBDZXJ0aWZpY2F0ZSBTZXJ2aWNlczCCASIwDQYJKoZIhvcNAQEBBQADggEP
            ADCCAQoCggEBAL5AnfRu4ep2hxxNRUSOvkbIgwadwSr+GB+O5AL686tdUIoWMQua
            BtDFcCLNSS1UY8y2bmhGC1Pqy0wkwLxyTurxFa70VJoSCsN6sjNg4tqJVfMiWPPe
            3M/vg4aijJRPn2jymJBGhCfHdr/jzDUsi14HZGWCwEiwqJH5YZ92IFCokcdmtet4
            YgNW8IoaE+oxox6gmf049vYnMlhvB/VruPsUK6+3qszWY19zjNoFmag4qMsXeDZR
            rOme9Hg6jc8P2ULimAyrL58OAd7vn5lJ8S3frHRNG5i1R8XlKdH5kBjHYpy+g8cm
            ez6KJcfA3Z3mNWgQIJ2P2N7Sw4ScDV7oL8kCAwEAAaOBwDCBvTAdBgNVHQ4EFgQU
            oBEKIz6W8Qfs4q8p74Klf9AwpLQwDgYDVR0PAQH/BAQDAgEGMA8GA1UdEwEB/wQF
            MAMBAf8wewYDVR0fBHQwcjA4oDagNIYyaHR0cDovL2NybC5jb21vZG9jYS5jb20v
            QUFBQ2VydGlmaWNhdGVTZXJ2aWNlcy5jcmwwNqA0oDKGMGh0dHA6Ly9jcmwuY29t
            b2RvLm5ldC9BQUFDZXJ0aWZpY2F0ZVNlcnZpY2VzLmNybDANBgkqhkiG9w0BAQUF
            AAOCAQEACFb8AvCb6P+k+tZ7xkSAzk/ExfYAWMymtrwUSWgEdujm7l3sAg9g1o1Q
            GE8mTgHj5rCl7r+8dFRBv/38ErjHT1r0iWAFf2C3BUrz9vHCv8S5dIa2LX1rzNLz
            Rt0vxuBqw8M0Ayx9lt1awg6nCpnBBYurDC/zXDrPbDdVCYfeU0BsWO/8tqtlbgT2
            G9w84FoVxp7Z8VlIMCFlA2zs6SFz7JsDoeA3raAVGI/6ugLOpyypEBMs1OUIJqsi
            l2D4kF501KKaU73yqWjgom7C12yxow+ev+to51byrvLjKzg6CYG1a4XXvi3tPxq3
            smPi9WIsgtRqAEFQ8TmDn5XpNpaYbg==
            -----END CERTIFICATE-----
            """;

    private static final String EM_DV_TLS_CA_G3B_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDaDCCAu6gAwIBAgIQAKSu5njyM2K0wORgt3LyFDAKBggqhkjOPQQDAzBWMQsw
            CQYDVQQGEwJJTjElMCMGA1UECgwcZU11ZGhyYSBUZWNobm9sb2dpZXMgTGltaXRl
            ZDEgMB4GA1UEAwwXZW1TaWduIFJvb3QgVExTIENBIC0gRzMwHhcNMjUwNDMwMTU1
            NDA4WhcNNDAwNDI4MTgzMDAwWjA/MQswCQYDVQQGEwJOTDETMBEGA1UECgwKZU11
            ZGhyYSBCVjEbMBkGA1UEAwwSRU0gRFYgVExTIENBIC0gRzNCMHYwEAYHKoZIzj0C
            AQYFK4EEACIDYgAE557ZwEqXc8RzXlERHahawr1eZKs3S4DhLLwgOOweTw47gS/J
            hZ4eZ6+wTi+LyX90hbplqpEe6KTkW2LVzAPVa+Z4s8OMtNyTfKJGDiOEU8iSk10p
            1E+R4VYGtjzO2rRoo4IBljCCAZIwHwYDVR0jBBgwFoAU4TqrIXzx401UrtRyt/2L
            WUIgkFQwHQYDVR0OBBYEFOIeL+ccGTkOVUtiwSAkVzUkiiUUMB0GA1UdJQQWMBQG
            CCsGAQUFBwMBBggrBgEFBQcDAjBdBgNVHSAEVjBUMDkGCysGAQQBg44hAQABMCow
            KAYIKwYBBQUHAgEWHGh0dHA6Ly9yZXBvc2l0b3J5LmVtc2lnbi5jb20wDQYLKwYB
            BAGDjiEBAmQwCAYGZ4EMAQIBMHUGCCsGAQUFBwEBBGkwZzAkBggrBgEFBQcwAYYY
            aHR0cDovL29jc3AtYi5lbXNpZ24uY29tMD8GCCsGAQUFBzAChjNodHRwOi8vcmVw
            b3NpdG9yeS5lbXNpZ24uY29tL2NlcnRzL0VNRFZUTFNDQUczQi5wN2MwNwYDVR0f
            BDAwLjAsoCqgKIYmaHR0cDovL2NybC5lbXNpZ24uY29tLz9yb290dGxzY2FnMy5j
            cmwwDgYDVR0PAQH/BAQDAgEGMBIGA1UdEwEB/wQIMAYBAf8CAQAwCgYIKoZIzj0E
            AwMDaAAwZQIwLmsGXi6oFMJ2klkcEMXMug6gvxQWZWkewiDWVzQyl2L6z6DuQfPO
            Ibh1grvdULDCAjEAxZvLlxUavG69J6wnSjC8XBbqNIQsJ6CE2QdNOe84Ptxb6VG8
            gt2TlfJbHii+KNvK
            -----END CERTIFICATE-----
            """;

    public static void main(String[] args) throws Exception {
        String subject = "CN=EM DV TLS CA - G3B, O=eMudhra BV, C=NL";
        Certificate[] affectedChain = new Certificate[] {parseCert(EM_DV_TLS_CA_G3B_PEM)};
        Certificate[] unrelatedChain = new Certificate[] {parseCert(EMSIGN_ECC_ROOT_G3_PEM)};
        check(CheckEmudhra.isBlockedCertificate(new javax.net.ssl.SSLHandshakeException("TLS failure")
                .initCause(new java.security.cert.CertPathBuilderException(
                        "unable to find valid certification path to requested target")),
                subject, affectedChain),
                "Root removal path-building rejection not recognized");
        check(!CheckEmudhra.isBlockedCertificate(new javax.net.ssl.SSLHandshakeException("TLS failure")
                .initCause(new java.security.cert.CertPathBuilderException(
                        "unable to find valid certification path to requested target")),
                subject, unrelatedChain),
                "Unrelated untrusted chain accepted as affected-root rejection proof");
        check(!CheckEmudhra.isBlockedCertificate(new javax.net.ssl.SSLHandshakeException("TLS failure")
                .initCause(new java.security.cert.CertPathBuilderException(
                        "unable to find valid certification path to requested target")),
                subject, null),
                "Missing peer chain accepted as affected-root rejection proof");
        check(CheckEmudhra.isBlockedCertificate(new javax.net.ssl.SSLHandshakeException("TLS failure")
                .initCause(new java.security.cert.CertPathValidatorException(
                        "Untrusted certificate: " + subject)),
                subject, affectedChain),
                "Explicit blocklist rejection not recognized");
        check(!CheckEmudhra.isBlockedCertificate(
                new java.security.cert.CertificateExpiredException(), subject, affectedChain),
                "Expired certificate accepted as rejection proof");
        check(!CheckEmudhra.isBlockedCertificate(
                new java.net.SocketTimeoutException(), subject, affectedChain),
                "Network error accepted as rejection proof");
        check(!CheckEmudhra.isBlockedCertificate(new java.security.cert.CertPathValidatorException(
                "Untrusted certificate: CN=Other CA"), subject, affectedChain),
                "Rejection of another CA accepted as rejection proof");
        System.out.println("PASS: strict rejection classification");

        Path systemCacerts = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
        Path directory = Files.createTempDirectory("remove-untrusted-roots-test");
        Path file = directory.resolve("cacerts");
        try {
            Files.copy(systemCacerts, file, StandardCopyOption.REPLACE_EXISTING);
            seedUntrustedRoots(file);
            Set<String> before = loadFingerprints(file);
            check(before.contains("86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B"),
                    "Test setup missing emSign ECC Root CA - G3");
            check(before.contains("D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4"),
                    "Test setup missing AAA Certificate Services");

            RemoveUntrustedCertificateRoots.remove(file);
            Set<String> after = loadFingerprints(file);

            for (String untrusted : RemoveUntrustedCertificateRoots.UNTRUSTED_ROOTS.keySet()) {
                check(!after.contains(untrusted), "Untrusted root still present: " + untrusted);
            }
            Set<String> expectedRemaining = new HashSet<>(before);
            expectedRemaining.removeAll(RemoveUntrustedCertificateRoots.UNTRUSTED_ROOTS.keySet());
            check(!expectedRemaining.isEmpty(), "Expected remaining trusted roots");
            check(after.equals(expectedRemaining), "Unrelated root certificates were modified or lost");

            byte[] first = Files.readAllBytes(file);
            RemoveUntrustedCertificateRoots.remove(file);
            check(Arrays.equals(first, Files.readAllBytes(file)), "Repeated run changed the file");

            // Regression test: JKS-formatted cacerts (magic 0xFEEDFEED, e.g. Debian/Ubuntu ca-certificates-java)
            writeJksFixture(file, systemCacerts);
            check(RemoveUntrustedCertificateRoots.isJks(Files.readAllBytes(file)), "Expected JKS fixture");
            RemoveUntrustedCertificateRoots.remove(file);
            Set<String> afterJks = loadFingerprints(file);
            for (String untrusted : RemoveUntrustedCertificateRoots.UNTRUSTED_ROOTS.keySet()) {
                check(!afterJks.contains(untrusted), "Untrusted root still present in JKS: " + untrusted);
            }
            check(afterJks.equals(expectedRemaining), "JKS unrelated root certificates were modified or lost");
            byte[] firstJks = Files.readAllBytes(file);
            RemoveUntrustedCertificateRoots.remove(file);
            check(Arrays.equals(firstJks, Files.readAllBytes(file)), "Repeated JKS run changed the file");

            for (String invalid : new String[] {"", "not-a-keystore"}) {
                Files.writeString(file, invalid);
                boolean rejected = false;
                try {
                    RemoveUntrustedCertificateRoots.remove(file);
                } catch (Exception expected) {
                    rejected = true;
                }
                check(rejected, "Invalid trust store accepted");
                check(invalid.equals(Files.readString(file)), "Invalid file modified");
            }

            Files.delete(file);
            boolean missingRejected = false;
            try {
                RemoveUntrustedCertificateRoots.remove(file);
            } catch (java.nio.file.NoSuchFileException expected) {
                missingRejected = true;
            }
            check(missingRejected, "Missing trust store accepted");
            System.out.println("PASS: preservation, root removal, idempotency and fail-closed checks");
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }

        if (args.length > 0 && "--runtime".equals(args[0])) {
            Set<String> runtimeRoots = loadFingerprints(systemCacerts);
            for (String untrusted : RemoveUntrustedCertificateRoots.UNTRUSTED_ROOTS.keySet()) {
                check(!runtimeRoots.contains(untrusted),
                        "Runtime cacerts still contains untrusted root: " + untrusted);
            }
            check(!CheckEmudhra.hasUntrustedRootInTrustStore(),
                    "Default TrustManager still trusts an untrusted root");
            check(!Files.isWritable(systemCacerts), "Runtime user can modify cacerts");
            check(Files.isWritable(Path.of("/app")), "Runtime group cannot write to /app");
            check(!Files.exists(Path.of("/app/RemoveUntrustedCertificateRoots.java")), "Build source not removed");
            check(!Files.exists(Path.of("/app/RemoveUntrustedCertificateRoots.class")), "Build class not removed");
            check(!Files.exists(Path.of("/app/RemoveTools.java")), "Tool cleanup source not removed");
            check(!Files.exists(Path.of("/app/RemoveTools.class")), "Tool cleanup class not removed");
            HttpsURLConnection connection = (HttpsURLConnection)
                    URI.create("https://github.com/").toURL().openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            try {
                connection.connect();
                check(connection.getServerCertificates().length > 0, "No trusted HTTPS chain");
            } finally {
                connection.disconnect();
            }
            System.out.println("PASS: runtime cacerts, permissions, cleanup and HTTPS to github.com");
        }
    }

    private static void seedUntrustedRoots(Path file) throws Exception {
        byte[] rawBytes = Files.readAllBytes(file);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = new java.io.ByteArrayInputStream(rawBytes)) {
            keyStore.load(input, null);
        }
        java.security.cert.CertificateFactory cf =
                java.security.cert.CertificateFactory.getInstance("X.509");
        keyStore.setCertificateEntry("emsigneccrootcag3 [jdk]",
                cf.generateCertificate(new java.io.ByteArrayInputStream(
                        EMSIGN_ECC_ROOT_G3_PEM.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        keyStore.setCertificateEntry("comodoaaaca [jdk]",
                cf.generateCertificate(new java.io.ByteArrayInputStream(
                        AAA_CERTIFICATE_SERVICES_PEM.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        char[] password = RemoveUntrustedCertificateRoots.isJks(rawBytes) ? "changeit".toCharArray() : null;
        java.io.ByteArrayOutputStream serialized = new java.io.ByteArrayOutputStream();
        keyStore.store(serialized, password);
        Files.write(file, serialized.toByteArray());
    }

    private static void writeJksFixture(Path target, Path sourceCacerts) throws Exception {
        KeyStore source = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(sourceCacerts)) {
            source.load(input, null);
        }
        KeyStore jks = KeyStore.getInstance("JKS");
        jks.load(null, "changeit".toCharArray());
        for (String alias : Collections.list(source.aliases())) {
            jks.setCertificateEntry(alias, source.getCertificate(alias));
        }
        java.io.ByteArrayOutputStream serialized = new java.io.ByteArrayOutputStream();
        jks.store(serialized, "changeit".toCharArray());
        Files.write(target, serialized.toByteArray());
        seedUntrustedRoots(target);
    }

    private static X509Certificate parseCert(String pem) throws Exception {
        return (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(
                        pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    private static Set<String> loadFingerprints(Path file) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(file)) {
            keyStore.load(input, null);
        }
        Set<String> fingerprints = new HashSet<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            Certificate certificate = keyStore.getCertificate(alias);
            check(certificate instanceof X509Certificate, "Non-X509 entry: " + alias);
            fingerprints.add(RemoveUntrustedCertificateRoots.fingerprint((X509Certificate) certificate));
        }
        return fingerprints;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
