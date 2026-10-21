import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fjerner rotsertifikater (Trust Anchors) vi ikke stoler på fra JVM-ens innebygde
 * tillitslager ({@code $JAVA_HOME/lib/security/cacerts}).
 *
 * <h2>Bakgrunn: Kompromitterte mellomsertifikater hos eMudhra (oktober 2026)</h2>
 * <p>
 * Privatnøklene til åtte mellomsertifikater (intermediate CAs) fra eMudhra
 * (seks for TLS: {@code EM DV/OV/EV TLS CA - G3B} og {@code G3C}, samt to for
 * kodesignering: {@code EM OV/EV CS CA - G3B}) ble kompromittert på grunn av
 * mangelfull nøkkelgenerering hos CA-en. Feilen gjør at hvem som helst kan
 * beregne privatnøklene direkte fra mellomsertifikatenes offentlige nøkler
 * (se <a href="https://emudhra.we-have-a-problem.com/">emudhra.we-have-a-problem.com</a>
 * og <a href="https://bugzilla.mozilla.org/show_bug.cgi?id=2083449">Mozilla Bug 2083449</a>).
 * </p>
 * <p>
 * Med disse privatnøklene kan en angriper utstede teknisk gyldige TLS-sertifikater
 * for ethvert domene og utføre Man-in-the-Middle-angrep (MITM) for å dekryptere og
 * lese eller manipulere kryptert TLS-trafikk fra klienter som stoler på kjeden.
 * </p>
 *
 * <h2>Hvorfor vi fjerner rotsertifikatene fra {@code cacerts} i stedet for å bruke {@code blocked.certs}</h2>
 * <p>
 * OpenJDKs interne blokkeringsfil ({@code $JAVA_HOME/lib/security/blocked.certs},
 * brukt av {@code sun.security.util.UntrustedCertificates}) sjekker kun SHA-256-fingeravtrykket
 * av hele det DER-kodede X.509-sertifikatet ({@code cert.getEncoded()}). I X.509 dekker
 * utstederens (rot-CA-ens) kryptografiske signatur kun {@code tbsCertificate}-delen,
 * og <em>ikke</em> de ytre feltene {@code signatureAlgorithm} og {@code signatureValue}.
 * </p>
 * <p>
 * En angriper trenger derfor ikke rot-CA-ens privatnøkkel for å lage muterte varianter av
 * de kompromitterte mellomsertifikatene som får helt andre SHA-256-fingeravtrykk (og dermed
 * omgår {@code blocked.certs}), men som fortsatt har gyldig signatur fra rot-CA-en og
 * godtas av Javas PKIX-validering:
 * </p>
 * <ul>
 *   <li><b>ECDSA-signaturmalleabilitet ({@code (r, s)} &rarr; {@code (r, n - s)}):</b>
 *       For mellomsertifikatene signert med {@code ecdsa-with-SHA384} av {@code emSign Root TLS CA - G3}
 *       er {@code (r, n - s mod n)} en matematisk gyldig, kanonisk DER-kodet ECDSA-signatur
 *       for nøyaktig samme {@code tbsCertificate}, men med ny SHA-256-hash.</li>
 *   <li><b>ASN.1 {@code NULL}-parameter i ytre {@code signatureAlgorithm}:</b>
 *       OpenJDKs {@code AlgorithmId.parse()} normaliserer både fraværende parameter og eksplisitt
 *       {@code NULL} ({@code 05 00}) til {@code null}. Ved å fjerne {@code 05 00} på RSA-kryssignerte
 *       sertifikater eller legge til {@code 05 00} på ECDSA-sertifikater endres sertifikatets
 *       SHA-256-hash uten at signaturvalideringen feiler.</li>
 *   <li><b>Ikke-minimale BER-lengdebytes i ytre {@code signatureAlgorithm}/{@code signatureValue}:</b>
 *       Gir angriperen mulighet til å generere uendelig mange ulike SHA-256-fingeravtrykk
 *       for samme mellomsertifikat.</li>
 * </ul>
 * <p>
 * Siden OpenJDK ikke har en innebygd mekanisme for å blokkere mellomsertifikater basert på
 * offentlig nøkkel ({@code SubjectPublicKeyInfo} / SPKI) slik nettleserne gjør via
 * CRLite/OneCRL/CRLSets, er fjerning av de underliggende rotsertifikatene fra {@code cacerts}
 * den eneste måten å garantere at JVM-en aldri kan bygge en gyldig sertifikatkjede
 * ({@code TrustAnchor}) til de kompromitterte mellomsertifikatene.
 * </p>
 *
 * <h2>Konsekvenser: Hvilke sider og tjenester vi potensielt ikke kan gjøre trafikk mot</h2>
 * <p>
 * Ved å fjerne disse rotsertifikatene fra {@code cacerts} vil utgående TLS-kall fra
 * Java-applikasjoner feile med {@code SunCertPathBuilderException} mot tjenester som
 * <em>utelukkende</em> presenterer en sertifikatkjede forankret i en av de fjernede røttene:
 * </p>
 * <ul>
 *   <li><b>{@code emSign ECC Root CA - G3} (alias {@code emsigneccrootcag3 [jdk]})</b> og
 *       <b>{@code emSign Root TLS/CS CA - G3}:</b>
 *       Rammer nettsteder og API-er som bruker TLS-sertifikater utstedt av den indiske
 *       sertifikatutstederen <b>eMudhra (emSign)</b> under deres ECC/G3-hierarki (uten
 *       kryssignatur fra en annen betrodd CA). eMudhra benyttes hovedsakelig av indiske
 *       bedrifter, banker og offentlige portaler i India samt enkelte aktører i Asia og
 *       Midtøsten. Det er ingen kjent bruk hos norske offentlige virksomheter, domstolene,
 *       europeiske skytjenester eller vanlige integrasjonspartnere. (Merk at eMudhras
 *       RSA-røtter {@code emSign Root CA - G1} og {@code G2} ikke er berørt av dette bruddet).</li>
 *   <li><b>{@code AAA Certificate Services} (alias {@code comodoaaaca [jdk]}):</b>
 *       Dette er en eldre SHA-1-rot fra 2004 operert av <b>Sectigo (tidligere Comodo CA)</b>,
 *       som Sectigo brukte til å kryssignere samtlige åtte kompromitterte eMudhra-mellomsertifikater.
 *       Å fjerne denne roten rammer:
 *       <ol>
 *         <li>Tjenester med eMudhra-sertifikater som baserer seg på kryssignaturen fra
 *             {@code AAA Certificate Services}.</li>
 *         <li>Eventuelle eldre eller feilkonfigurerte tjenester med sertifikater fra
 *             Sectigo/Comodo som <em>kun</em> sender en utdatert kryssignert kjede opp til
 *             {@code AAA Certificate Services}, i stedet for å kjede til nyere Sectigo-røtter.</li>
 *       </ol>
 *       <b>Viktig:</b> Både Google Chrome (Chrome Root Store) og Mozilla Firefox (NSS) har
 *       allerede fjernet {@code AAA Certificate Services} som betrodd rot. Alle offentlige
 *       nettsteder og API-er med Sectigo/Comodo-sertifikater som fungerer i moderne nettlesere,
 *       kjeder derfor allerede til nyere røtter som fortsatt ligger i {@code cacerts}
 *       ({@code USERTrust RSA/ECC Certification Authority}, {@code COMODO RSA/ECC Certification Authority}
 *       eller {@code Sectigo Public Server Authentication Root R46/E46}).</li>
 * </ul>
 */
public class RemoveUntrustedCertificateRoots {
    static final Map<String, String> UNTRUSTED_ROOTS;

    static {
        Map<String, String> roots = new LinkedHashMap<>();
        // emSign ECC Root CA - G3 (i cacerts som "emsigneccrootcag3 [jdk]"):
        // Kryssignerte "emSign Root TLS CA - G3", som utstedte de kompromitterte G3B/G3C-mellomsertifikatene.
        roots.put(
                "86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B",
                "CN=emSign ECC Root CA - G3, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN");
        // AAA Certificate Services (i cacerts som "comodoaaaca [jdk]"):
        // Sectigo/Comodo-rot som kryssignerte samtlige 8 kompromitterte eMudhra-mellomsertifikater.
        roots.put(
                "D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4",
                "CN=AAA Certificate Services, O=Comodo CA Limited, L=Salford, ST=Greater Manchester, C=GB");
        // emSign Root TLS CA - G3 (direkte utsteder av de 6 kompromitterte TLS-mellomsertifikatene;
        // ikke i OpenJDK cacerts per i dag, men inkludert for å sikre at den aldri blir betrodd):
        roots.put(
                "7DD78D5F4F13459A83DFF9ABBBA62EDBAF6F2D102BF257FD712F4D9F2746ED8D",
                "CN=emSign Root TLS CA - G3, O=eMudhra Technologies Limited, C=IN");
        // emSign Root CS CA - G3 (direkte utsteder av de 2 kompromitterte kodesignerings-mellomsertifikatene;
        // ikke i OpenJDK cacerts per i dag, men inkludert for å sikre at den aldri blir betrodd):
        roots.put(
                "F2EDAAFF3D9B701FB4D14C9F285F8C0BAD90FC3BCC1FA07D47F4718EDABA34A5",
                "CN=emSign Root CS CA - G3, O=eMudhra Technologies Limited, C=IN");
        UNTRUSTED_ROOTS = Collections.unmodifiableMap(roots);
    }

    public static void main(String[] args) throws Exception {
        Path cacerts = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
        remove(cacerts);
        Files.setPosixFilePermissions(Path.of("/app"),
                java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxr-x"));
        Files.deleteIfExists(Path.of("/app/RemoveUntrustedCertificateRoots.java"));
        Files.deleteIfExists(Path.of("/app/RemoveUntrustedCertificateRoots.class"));
    }

    static void remove(Path cacerts) throws Exception {
        for (String fingerprint : UNTRUSTED_ROOTS.keySet()) {
            if (!fingerprint.matches("[0-9A-F]{64}")) {
                throw new IllegalStateException("Invalid SHA-256 fingerprint: " + fingerprint);
            }
        }
        byte[] rawBytes = Files.readAllBytes(cacerts);
        if (rawBytes.length == 0) {
            throw new IllegalStateException("Empty cacerts trust store: " + cacerts);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (ByteArrayInputStream input = new ByteArrayInputStream(rawBytes)) {
            keyStore.load(input, null);
        }

        List<String> removed = new ArrayList<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509)) {
                throw new IllegalStateException("Non-X509 certificate in cacerts under alias: " + alias);
            }
            String sha256 = fingerprint(x509);
            if (UNTRUSTED_ROOTS.containsKey(sha256)) {
                keyStore.deleteEntry(alias);
                removed.add(alias + " (" + sha256 + ")");
            }
        }

        if (keyStore.size() == 0) {
            throw new IllegalStateException("Refusing to write empty cacerts trust store: " + cacerts);
        }

        if (!removed.isEmpty()) {
            char[] storePassword = isJks(rawBytes) ? "changeit".toCharArray() : null;
            ByteArrayOutputStream serialized = new ByteArrayOutputStream();
            keyStore.store(serialized, storePassword);
            Files.write(cacerts, serialized.toByteArray(),
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        System.out.println("Removed " + removed.size() + " untrusted root certificate(s) from "
                + cacerts + ": " + removed);
    }

    static boolean isJks(byte[] bytes) {
        return bytes.length >= 4
                && (bytes[0] & 0xFF) == 0xFE
                && (bytes[1] & 0xFF) == 0xED
                && (bytes[2] & 0xFF) == 0xFE
                && (bytes[3] & 0xFF) == 0xED;
    }

    static String fingerprint(X509Certificate certificate) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }
}
