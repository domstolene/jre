import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

public class CheckEmudhra {
    private static final Set<String> AFFECTED_TLS_KEYS = Set.of(
            "EBED6475C2C2D80FCB199F3FE8DD6E6B3CE60B77EB15D4E42C3DEC0C6F5B5105",
            "43BFFA19D6EF651451F6841E019931E96F6F2EAEFF28AB4B6F1CE4576DE2717B",
            "E0254C7FCDC265E2F0F8A428DABB75AB4AE039B4872D62F5EE57F0877D5A8A41",
            "49DD353DC2B919A3770645117D0E60C16115E7D1BC259EEFE734B00EA434FE5F",
            "63ECEFEE431D49523BC1136C4E72D0206A477086CAF606CD5CF025686C109CF8",
            "5C0660A2B164910D8FF589C18E14552E9E0EC237DCEFE2CB736D41A4811F81A0");

    private static final Set<String> UNTRUSTED_ROOTS = Set.of(
            "86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B",
            "D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4",
            "7DD78D5F4F13459A83DFF9ABBBA62EDBAF6F2D102BF257FD712F4D9F2746ED8D",
            "F2EDAAFF3D9B701FB4D14C9F285F8C0BAD90FC3BCC1FA07D47F4718EDABA34A5");

    public static void main(String[] args) throws Exception {
        boolean requireBlocked = args.length == 1 && "--require-blocked".equals(args[0]);
        System.out.println("Java: " + System.getProperty("java.runtime.version"));
        System.out.println("Time: " + java.time.Instant.now());
        System.out.println("Trust store: "
                + System.getProperty("javax.net.ssl.trustStore", "JVM default"));
        System.out.println("com.sun.net.ssl.checkRevocation: "
                + System.getProperty("com.sun.net.ssl.checkRevocation", "false"));
        System.out.println("ocsp.enable: " + java.security.Security.getProperty("ocsp.enable"));

        X509ExtendedTrustManager defaultTrustManager = defaultTrustManager();
        boolean rootsRemoved = !hasUntrustedRootInTrustStore(defaultTrustManager);
        int trusted = 0;
        int rejected = 0;
        int unknown = 0;
        for (String category : new String[] {"dv", "ov", "ev"}) {
            for (String region : new String[] {"g3b", "g3c"}) {
                for (String issuer : new String[] {"", "-aaa"}) {
                    String host = category + "-" + region + issuer
                            + "-emudhra.we-have-a-problem.com";
                    AtomicReference<X509Certificate[]> presentedChain = new AtomicReference<>();
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, new TrustManager[] {
                            capturingTrustManager(defaultTrustManager, presentedChain)
                    }, null);
                    for (int attempt = 1; attempt <= 5; attempt++) {
                        presentedChain.set(null);
                        try (Socket transport = new Socket()) {
                            transport.connect(new InetSocketAddress(host, 443), 5_000);
                            try (SSLSocket socket = (SSLSocket) sslContext.getSocketFactory()
                                    .createSocket(transport, host, 443, true)) {
                                socket.setSoTimeout(5_000);
                                SSLParameters parameters = socket.getSSLParameters();
                                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                                socket.setSSLParameters(parameters);
                                socket.startHandshake();
                                if (hasAffectedKey(socket.getSession().getPeerCertificates())) {
                                    trusted++;
                                    System.out.println("TRUSTED " + host + " - compromised CA key accepted");
                                } else {
                                    unknown++;
                                    System.out.println("UNKNOWN " + host
                                            + " - no affected key in peer chain; possible TLS interception");
                                }
                                break;
                            }
                        } catch (SSLHandshakeException exception) {
                            boolean certificateFailure = false;
                            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                                certificateFailure |= cause instanceof CertificateException;
                            }
                            if (!certificateFailure && attempt < 5) {
                                Thread.sleep(1_000L * attempt);
                                continue;
                            }
                            String expectedSubject = "CN=EM " + category.toUpperCase(java.util.Locale.ROOT)
                                + " TLS CA - " + region.toUpperCase(java.util.Locale.ROOT)
                                + ("g3b".equals(region) ? ", O=eMudhra BV, C=NL" : ", O=eMudhra Inc, C=US");
                            if (certificateFailure && (!requireBlocked
                                || (rootsRemoved && isBlockedCertificate(
                                        exception, expectedSubject, presentedChain.get())))) {
                                rejected++;
                                System.out.println("REJECTED " + host + " - " + exception.getMessage());
                            } else {
                                unknown++;
                                System.out.println("UNKNOWN " + host + " - " + exception);
                            }
                            break;
                        } catch (IOException exception) {
                            if (attempt < 5) {
                                Thread.sleep(1_000L * attempt);
                                continue;
                            }
                            unknown++;
                            System.out.println("UNKNOWN " + host + " - " + exception);
                        }
                    }
                }
            }
        }
        System.out.printf("Result: TRUSTED=%d REJECTED=%d UNKNOWN=%d%n", trusted, rejected, unknown);
        System.out.println("Only the 12 published TLS endpoints were tested, not code signing or past attacks.");
        System.out.println(requireBlocked
                ? "REJECTED requires untrusted eMudhra/AAA roots to be removed from cacerts and path building to fail."
                : "REJECTED describes this handshake, not proof of specific root removal.");
        System.exit(trusted > 0 ? 1 : unknown > 0 ? 2 : 0);
    }

    static X509ExtendedTrustManager defaultTrustManager() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509ExtendedTrustManager extended) {
                return extended;
            }
        }
        throw new IllegalStateException("No X509ExtendedTrustManager found");
    }

    static X509ExtendedTrustManager capturingTrustManager(
            X509ExtendedTrustManager delegate, AtomicReference<X509Certificate[]> captured) {
        return new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                    throws CertificateException {
                delegate.checkClientTrusted(chain, authType, socket);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                    throws CertificateException {
                captured.set(chain);
                delegate.checkServerTrusted(chain, authType, socket);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                    throws CertificateException {
                delegate.checkClientTrusted(chain, authType, engine);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                    throws CertificateException {
                captured.set(chain);
                delegate.checkServerTrusted(chain, authType, engine);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                delegate.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                captured.set(chain);
                delegate.checkServerTrusted(chain, authType);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return delegate.getAcceptedIssuers();
            }
        };
    }

    static boolean hasUntrustedRootInTrustStore() throws Exception {
        return hasUntrustedRootInTrustStore(defaultTrustManager());
    }

    static boolean hasUntrustedRootInTrustStore(X509TrustManager trustManager) throws Exception {
        for (X509Certificate issuer : trustManager.getAcceptedIssuers()) {
            String sha256 = HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(issuer.getEncoded()));
            if (UNTRUSTED_ROOTS.contains(sha256)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasAffectedKey(Certificate[] chain) throws Exception {
        if (chain == null) {
            return false;
        }
        for (Certificate certificate : chain) {
            if (certificate instanceof X509Certificate x509) {
                String keyHash = HexFormat.of().withUpperCase().formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(x509.getPublicKey().getEncoded()));
                if (AFFECTED_TLS_KEYS.contains(keyHash)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isBlockedCertificate(
            Throwable exception, String expectedSubject, Certificate[] chain) throws Exception {
        if (!hasAffectedKey(chain)) {
            return false;
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof CertPathBuilderException
                    && "unable to find valid certification path to requested target".equals(cause.getMessage())) {
                return true;
            }
            if (cause instanceof CertPathValidatorException
                    && ("Untrusted certificate: " + expectedSubject).equals(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }
}