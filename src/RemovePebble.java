/**
 * Ettersom vi ikke har shell i base image, må vi trikse og bruke Java
 * for å slette pebble-binær, som vi ikke trenger.
 */
import java.io.File;

void main() {
    String[] targets = {"/bin/pebble", "/usr/bin/pebble", "/var/lib/pebble"};
    for (String path : targets) {
        File file = new File(path);
        if (file.exists()) {
            System.out.println("Deleting " + path + ", result: " + deleteRecursive(file));
        }
    }

    // Slett oss selv til slutt
    File me = new File("/app/RemovePebble.class");
    if (me.exists()) {
        System.out.println("Deleting self, result: " + me.delete());
    } else {
        System.out.println("Self file not found: " + me.getAbsolutePath());
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
