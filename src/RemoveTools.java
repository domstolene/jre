/**
 * Ettersom vi ikke har shell i base image, må vi trikse og bruke Java
 * for å slette verktøy som vi ikke trenger i image.
 */
import java.io.File;

void main() {
    String[] toolsToRemove = {
        // Oppstart
        "pebble",   // Pebble er et verktøy for å starte applikasjoner, som vi ikke trenger

        // Utvikling og kompilering
        "javac",    // Java-kompilatoren
        "jshell",   // Interaktivt Java-skall (REPL)
        "jlink",    // Verktøy for å lage tilpassede JREs
        "jmod",     // Håndterer Java-moduler
        "javadoc",  // Genererer dokumentasjon
        "jar",      // Pakkeverktøy for .jar-filer

        // Feilsøking, profilering og overvåking (Svært attraktive for angripere)
        "jcmd",     // Sender kontrollkommandoer til en kjørende JVM
        "jdb",      // Java-debuggeren
        "jhsdb",    // Avansert "Serviceability Agent" debugger
        "jinfo",    // Genererer konfigurasjonsinformasjon fra en prosess
        "jmap",     // Dumper minnet (Memory/Heap dump)
        "jstack",   // Dumper alle tråder (Thread stack dump)
        "jstat",    // Overvåker ytelsesstatistikk
        "jstatd",   // RMI-server for jstat
        "jimage",   // Verktøy for å pakke ut/vis jimage-filer
        "jpackage"  // Pakker applikasjoner inn i native formater
    };

    String path = System.getenv("PATH");
    System.out.println("Gjeldende PATH:\n" + path);

    for (String tool : toolsToRemove) {
        Optional<File> foundTool = finnProgramPaPath(tool);
        if (foundTool.isPresent()) {
            File file = foundTool.get();
            String pathToDelete = file.getAbsolutePath();
            System.out.println("Deleting " + pathToDelete + ", result: " + deleteRecursive(file));
        } else {
            System.out.println("Could not find '" + tool + "' on PATH.");
        }
    }

    // fjern alle pebble-relaterte filer i /var/lib/pebble
    var result = deleteRecursive(new File("/var/lib/pebble"));
    System.out.println("Deleting /var/lib/pebble, result: " + result);

    // Slett oss selv til slutt
    File me = new File("/app/RemoveTools.class");
    if (me.exists()) {
        System.out.println("Deleting self, result: " + me.delete());
    } else {
        System.out.println("Self file not found: " + me.getAbsolutePath());
    }
}

/**
 * Leter etter et program i systemets PATH (kun Unix-støtte).
 */
Optional<File> finnProgramPaPath(String programNavn) {
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null || pathEnv.isEmpty()) {
        return Optional.empty();
    }

    // Unix bruker alltid kolon (:) som skilletegn i PATH
    String[] mapper = pathEnv.split(":");

    for (String mappe : mapper) {
        File fil = new File(mappe, programNavn);
        if (fil.exists() && fil.isFile()) {
            return Optional.of(fil);
        }
    }

    return Optional.empty();
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
