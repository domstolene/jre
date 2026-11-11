import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;

public class CheckEmudhraTest {
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
        if (args.length > 0 && "--runtime".equals(args[0])) {
            check(!CheckEmudhra.hasUntrustedRootInTrustStore(),
                    "Default TrustManager still trusts an untrusted root");
            check(!Files.isWritable(systemCacerts), "Runtime user can modify cacerts");
            check(Files.isWritable(Path.of("/app")), "Runtime group cannot write to /app");
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

    private static X509Certificate parseCert(String pem) throws Exception {
        return (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(
                        pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
