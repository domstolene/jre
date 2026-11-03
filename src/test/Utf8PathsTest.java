import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Utf8PathsTest {
    public static void main(String[] args) throws Exception {
        // Samme filsti som ga InvalidPathException ved nedlasting fra SFTP.
        Path original = Path.of("/data/sftpdata/tmp/Begjæring om utsettelse av rettsmøte");
        requireUtf8("native.encoding");
        requireUtf8("sun.jnu.encoding");

        Path directory = Files.createTempDirectory("utf8-paths-test-");
        Path file = directory.resolve(original.getFileName() + " æøå ÆØÅ.txt");
        String content = "Begjæring om utsettelse av rettsmøte – æøå ÆØÅ";
        try {
            Files.writeString(file, content);
            if (!content.equals(Files.readString(file))) {
                throw new AssertionError("Filinnhold ble ikke bevart");
            }
            // Les filnavnet tilbake fra filsystemet, ikke bare fra Java-strengen.
            try (var entries = Files.list(directory)) {
                if (!entries.toList().equals(java.util.List.of(file))) {
                    throw new AssertionError("Filnavnet ble ikke bevart");
                }
            }
            System.out.println("PASS: UTF-8-filstier og filinnhold: " + file.getFileName());
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
    }

    private static void requireUtf8(String property) {
        String encoding = System.getProperty(property);
        if (encoding == null || !StandardCharsets.UTF_8.equals(Charset.forName(encoding))) {
            throw new AssertionError(property + " må være UTF-8, var " + encoding);
        }
    }
}
