package vimdojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Where everything the app stores lives: ~/.vimdojo unless it has been moved. Moving it leaves
 * a one-line file, ~/.vimdojo-location, holding the new path; moving it back home removes that
 * file again. For tests, -Dvimdojo.dir=... overrides all of this for the session.
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

    /** A typed folder, with a leading ~ read as the home folder; null unless it is a full path. */
    static Path parse(String typed) {
        String text = typed.strip();
        if (text.equals("~")) {
            return home();
        }
        if (text.startsWith("~/") || text.startsWith("~" + File.separator)) {
            text = home() + text.substring(1);
        }
        try {
            Path path = Path.of(text);
            return path.isAbsolute() ? path.normalize() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Moves the app's files to the folder typed, and from then on uses it. Returns why it
     * couldn't, or null once it has.
     */
    static String move(String typed) {
        Path to = parse(typed);
        Path from = current();
        if (to == null) {
            return "Use a full path, such as " + (File.separatorChar == '/'
                    ? "~/Documents/vimdojo" : "C:\\Users\\you\\Documents\\vimdojo");
        }
        if (to.equals(from)) {
            return "That's where it is already";
        }
        if (Files.exists(to) && !Files.isDirectory(to)) {
            return "That's a file, not a folder";
        }
        for (String name : FILES) {
            if (Files.exists(to.resolve(name))) {
                return "That folder already has vimdojo data in it";
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
