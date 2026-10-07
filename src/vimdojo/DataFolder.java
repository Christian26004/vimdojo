package vimdojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Where everything the app stores lives: ~/.vimdojo unless it has been moved. It moves into a
 * folder picked in settings, as a .vimdojo folder inside it. Moving it leaves a one-line file,
 * ~/.vimdojo-location, holding the new path; moving it back home removes that file again. For
 * tests, -Dvimdojo.dir=... overrides all of this for the session.
 */
final class DataFolder {
    /** Every file the app writes, and so every file a move carries. */
    static final List<String> FILES = List.of("settings.properties", "history.tsv", "tasks.tsv");

    private static final String OVERRIDE = "vimdojo.dir";

    private DataFolder() {
    }

    private static Path home() {
        return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    /** The folder used when nothing says otherwise. */
    static Path standard() {
        return home().resolve(".vimdojo");
    }

    /** The file that remembers a moved folder's place. */
    static Path pointer() {
        return home().resolve(".vimdojo-location");
    }

    static Path current() {
        String override = System.getProperty(OVERRIDE);
        if (override != null) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        try {
            if (Files.exists(pointer())) {
                String saved = Files.readString(pointer()).strip();
                if (!saved.isEmpty()) {
                    return Path.of(saved).toAbsolutePath().normalize();
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("vimdojo: ignoring unreadable " + pointer() + ": " + e.getMessage());
        }
        return standard();
    }

    /**
     * A folder as people would write it: under the home folder as ~/..., except on Windows,
     * where the full path is what people recognise.
     */
    static String shown(Path dir) {
        dir = dir.toAbsolutePath().normalize();
        if (File.separatorChar == '/' && dir.startsWith(home()) && !dir.equals(home())) {
            return "~/" + home().relativize(dir);
        }
        return dir.toString();
    }

    static String shown() {
        return shown(current());
    }

    /** Where the folder goes when a place is picked for it: a .vimdojo folder inside it. */
    static Path inside(Path picked) {
        Path dir = picked.toAbsolutePath().normalize();
        Path name = dir.getFileName();
        return name != null && name.toString().equals(".vimdojo") ? dir : dir.resolve(".vimdojo");
    }

    /**
     * Moves the app's files into the folder given, and from then on uses it. Returns why it
     * couldn't, or null once it has.
     */
    static String move(Path to) {
        to = to.toAbsolutePath().normalize();
        Path from = current();
        if (to.equals(from)) {
            return "Your data is already there";
        }
        if (Files.exists(to) && !Files.isDirectory(to)) {
            return "There's a file called .vimdojo there already";
        }
        for (String name : FILES) {
            if (Files.exists(to.resolve(name))) {
                return "There's vimdojo data there already";
            }
        }
        try {
            Files.createDirectories(to);
            for (String name : FILES) {
                if (Files.exists(from.resolve(name))) {
                    Files.copy(from.resolve(name), to.resolve(name),
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            pointAt(to);
        } catch (IOException | RuntimeException e) {
            return "Couldn't move it: " + e.getMessage();
        }
        // Everything is safely in the new place; tidy up the old one.
        try {
            for (String name : FILES) {
                Files.deleteIfExists(from.resolve(name));
            }
            Files.deleteIfExists(from);
        } catch (DirectoryNotEmptyException e) {
            // Other things live there too; leave the folder itself alone.
        } catch (IOException e) {
            System.err.println("vimdojo: could not clear " + from + ": " + e.getMessage());
        }
        return null;
    }

    private static void pointAt(Path to) throws IOException {
        if (System.getProperty(OVERRIDE) != null) {
            System.setProperty(OVERRIDE, to.toString());
        } else if (to.equals(standard())) {
            Files.deleteIfExists(pointer());
        } else {
            Files.writeString(pointer(), to + System.lineSeparator());
        }
    }
}
