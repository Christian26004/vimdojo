package vimdojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

/** Append-only log of every finished lesson run, stored as tab-separated text. */
final class History {
    private final Path file;
    private final List<Attempt> attempts = new ArrayList<>();

    private History(Path file) {
        this.file = file;
    }

    static History load() {
        History h = new History(Settings.dataDir().resolve("history.tsv"));
        if (Files.exists(h.file)) {
            try {
                for (String line : Files.readAllLines(h.file)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        h.attempts.add(Attempt.fromLine(line));
                    } catch (IllegalArgumentException e) {
                        System.err.println("vimdojo: skipping bad history line: " + line);
                    }
                }
            } catch (IOException e) {
                System.err.println("vimdojo: could not read history: " + e.getMessage());
            }
        }
        return h;
    }

    /** Oldest first. */
    List<Attempt> all() {
        return Collections.unmodifiableList(attempts);
    }

    long runs(String lesson) {
        return attempts.stream().filter(a -> a.lesson().equals(lesson)).count();
    }

    OptionalDouble bestEfficiency(String lesson) {
        return attempts.stream().filter(a -> a.lesson().equals(lesson))
                .mapToDouble(Attempt::efficiency).max();
    }

    OptionalDouble bestSeconds(String lesson) {
        return attempts.stream().filter(a -> a.lesson().equals(lesson))
                .mapToDouble(Attempt::seconds).min();
    }

    /** Forgets every run, in memory and on disk. */
    void clear() {
        attempts.clear();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("vimdojo: could not erase history: " + e.getMessage());
        }
    }

    void add(Attempt attempt) {
        attempts.add(attempt);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, attempt.toLine() + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("vimdojo: could not save result: " + e.getMessage());
        }
    }
}
