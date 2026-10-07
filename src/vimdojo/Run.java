package vimdojo;

import java.util.ArrayList;
import java.util.List;

/** One attempt at a lesson: feeds keys to Vim and keeps score per task. */
final class Run {
    private final Lesson lesson;
    private final List<Task> tasks;
    private final boolean guided;
    private final int[] keys;
    private final long[] millis;
    private final StringBuilder[] typed;
    private Vim vim;
    private int index;
    private long taskStart = -1;
    private boolean waiting;

    /** Guided runs show each task's hint; the tasks come already made for this run. */
    Run(Lesson lesson, List<Task> tasks, boolean guided) {
        this.lesson = lesson;
        this.tasks = List.copyOf(tasks);
        this.guided = guided;
        this.keys = new int[tasks.size()];
        this.millis = new long[tasks.size()];
        this.typed = new StringBuilder[tasks.size()];
        for (int i = 0; i < typed.length; i++) {
            typed[i] = new StringBuilder();
        }
        Task first = tasks.get(0);
        vim = new Vim(first.start(), first.row(), first.col());
    }

    Lesson lesson() {
        return lesson;
    }

    List<Task> tasks() {
        return tasks;
    }

    boolean guided() {
        return guided;
    }

    Task task() {
        return tasks.get(Math.min(index, tasks.size() - 1));
    }

    Vim vim() {
        return vim;
    }

    int index() {
        return index;
    }

    int keys(int task) {
        return keys[task];
    }

    /** Every key pressed during a task, in order. */
    String typed(int task) {
        return typed[task].toString();
    }

    double seconds(int task) {
        return millis[task] / 1000.0;
    }

    /** True between finishing a task and {@link #advance()}; keys are ignored meanwhile. */
    boolean waiting() {
        return waiting;
    }

    boolean finished() {
        return index == tasks.size();
    }

    /** Starts the clock for the current task; call when it appears on screen. */
    void shown(long now) {
        taskStart = now;
    }

    /** Handles one key. Returns true when this key completed the task. */
    boolean key(char c, long now) {
        if (waiting || finished()) {
            return false;
        }
        if (taskStart < 0) {
            taskStart = now;
        }
        keys[index]++;
        typed[index].append(c);
        vim.key(c);
        if (task().reached(vim)) {
            millis[index] = now - taskStart;
            waiting = true;
        }
        return waiting;
    }

    void advance() {
        waiting = false;
        taskStart = -1;
        index++;
        if (finished()) {
            return;
        }
        Task next = task();
        // Carrying on in the same buffer keeps the last search and find, so n and ; still work.
        if (!next.start().equals(vim.text()) || next.row() != vim.row() || next.col() != vim.col()) {
            vim = new Vim(next.start(), next.row(), next.col());
        }
    }

    /** One line per task for the history, filed under the lesson each task came from. */
    List<History.TaskResult> results(long timestamp) {
        List<History.TaskResult> results = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            results.add(new History.TaskResult(timestamp, tasks.get(i).lesson(), keys[i],
                    tasks.get(i).par(), millis[i] / 1000.0));
        }
        return results;
    }

    Attempt attempt(long timestamp) {
        int totalKeys = 0;
        int par = 0;
        long totalMillis = 0;
        for (int i = 0; i < tasks.size(); i++) {
            totalKeys += keys[i];
            par += tasks.get(i).par();
            totalMillis += millis[i];
        }
        return new Attempt(timestamp, lesson.id(), totalMillis / 1000.0, totalKeys, par,
                tasks.size());
    }
}
