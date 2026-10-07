package vimdojo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** User preferences, kept in a properties file next to the history. */
final class Settings {
    String theme = Theme.ALL[0].name();
    /** Index of the lesson to open next. */
    int lesson;
    /** Typed text is converted from QWERTY to Dvorak, as Vim's keymap=dvorak does. */
    boolean dvorak;
    /** The first-run tour has been seen to the end or skipped. */
    boolean finishedGuide;

    private final Path file;

    private Settings(Path file) {
        this.file = file;
    }

    /** Everything the app stores lives here; override with -Dvimdojo.dir=... */
    static Path dataDir() {
        String override = System.getProperty("vimdojo.dir");
        return override != null ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".vimdojo");
    }

    static Settings load() {
        Settings s = new Settings(dataDir().resolve("settings.properties"));
        if (Files.exists(s.file)) {
            Properties p = new Properties();
            try (Reader in = Files.newBufferedReader(s.file)) {
                p.load(in);
                s.theme = p.getProperty("theme", s.theme);
                s.dvorak = Boolean.parseBoolean(p.getProperty("dvorak"));
                s.finishedGuide = Boolean.parseBoolean(p.getProperty("finished_guide"));
                // Saved as the lesson's id; older versions saved its position in the list.
                String lesson = p.getProperty("lesson", "0");
                s.lesson = lesson.matches("\\d+")
                        ? Math.max(0, Math.min(Integer.parseInt(lesson), Lessons.ALL.size() - 1))
                        : Math.max(0, Lessons.indexOf(lesson));
            } catch (IOException | IllegalArgumentException e) {
                System.err.println("vimdojo: ignoring unreadable settings: " + e.getMessage());
            }
        }
        return s;
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("theme", theme);
        p.setProperty("lesson", Lessons.ALL.get(lesson).id());
        p.setProperty("dvorak", Boolean.toString(dvorak));
        p.setProperty("finished_guide", Boolean.toString(finishedGuide));
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file)) {
                p.store(out, "vimdojo settings");
            }
        } catch (IOException e) {
            System.err.println("vimdojo: could not save settings: " + e.getMessage());
        }
    }
}
