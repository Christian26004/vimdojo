package vimdojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Append-only logs of every finished lesson run, stored as tab-separated text: one line per run,
 * and one per task. The task lines say which lesson each task came from, so mixed runs count
 * toward the lessons they drew on.
 */
final class History {
    /** How many of a lesson's latest tasks its recent efficiency is taken from. */
    private static final int RECENT_TASKS = 12;

    /** One task of a finished run. */
    record TaskResult(long timestamp, String lesson, int keys, int par, double seconds) {
        double efficiency() {
            return keys == 0 ? 0 : 100.0 * par / keys;
        }

        String toLine() {
            return String.format(Locale.ROOT, "%d\t%s\t%d\t%d\t%.2f", timestamp, lesson, keys,
                    par, seconds);
        }

        static TaskResult fromLine(String line) {
            String[] f = line.split("\t");
            if (f.length != 5) {
                throw new IllegalArgumentException("expected 5 fields, got " + f.length);
            }
            return new TaskResult(Long.parseLong(f[0]), f[1], Integer.parseInt(f[2]),
                    Integer.parseInt(f[3]), Double.parseDouble(f[4]));
        }
    }

    private final Path file;
    private final Path taskFile;
    private final List<Attempt> attempts = new ArrayList<>();
    private final List<TaskResult> tasks = new ArrayList<>();

    private History(Path file, Path taskFile) {
        this.file = file;
        this.taskFile = taskFile;
    }

    static History load() {
        History h = new History(Settings.dataDir().resolve("history.tsv"),
                Settings.dataDir().resolve("tasks.tsv"));
        read(h.file, Attempt::fromLine, h.attempts);
        read(h.taskFile, TaskResult::fromLine, h.tasks);
        return h;
    }

    private static <T> void read(Path file, Function<String, T> parse, List<T> into) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    into.add(parse.apply(line));
                } catch (IllegalArgumentException e) {
                    System.err.println("vimdojo: skipping bad history line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("vimdojo: could not read history: " + e.getMessage());
        }
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

    /**
     * Each lesson's recent efficiency: the average over its latest tasks, wherever they were
     * played. Lessons only played before tasks were logged fall back to their latest runs.
     * Lessons never played are left out.
     */
    Map<String, Double> recentEfficiency() {
        Map<String, List<Double>> recent = new HashMap<>();
        for (int i = tasks.size() - 1; i >= 0; i--) {
            TaskResult t = tasks.get(i);
            List<Double> list = recent.computeIfAbsent(t.lesson(), k -> new ArrayList<>());
            if (list.size() < RECENT_TASKS) {
                list.add(t.efficiency());
            }
        }
        java.util.Set<String> logged = new java.util.HashSet<>(recent.keySet());
        for (int i = attempts.size() - 1; i >= 0; i--) {
            Attempt a = attempts.get(i);
            Lesson lesson = Lessons.byId(a.lesson());
            if (lesson != null && !lesson.isMix() && !logged.contains(a.lesson())) {
                List<Double> list = recent.computeIfAbsent(a.lesson(), k -> new ArrayList<>());
                if (list.size() < 3) {
                    list.add(a.efficiency());
                }
            }
        }
        Map<String, Double> efficiency = new HashMap<>();
        recent.forEach((lesson, list) -> efficiency.put(lesson,
                list.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        return efficiency;
    }

    /** Forgets every run, in memory and on disk. */
    void clear() {
        attempts.clear();
        tasks.clear();
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(taskFile);
        } catch (IOException e) {
            System.err.println("vimdojo: could not erase history: " + e.getMessage());
        }
    }

    void add(Attempt attempt, List<TaskResult> results) {
        attempts.add(attempt);
        tasks.addAll(results);
        append(file, List.of(attempt.toLine()));
        append(taskFile, results.stream().map(TaskResult::toLine).toList());
    }

    private static void append(Path file, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, String.join(System.lineSeparator(), lines)
                    + System.lineSeparator(), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("vimdojo: could not save result: " + e.getMessage());
        }
    }
}
