package vimdojo;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Dependency-free checks of the emulator and the curriculum. Run with ./test.sh */
public final class LessonTest {
    private static int checks;

    public static void main(String[] args) {
        everyLessonCanBeSolvedAtPar();
        drillsTeachTheirKeys();
        vimBasics();
        scoring();
        System.out.println("ok - " + checks + " checks passed");
    }

    /** Plays each task's stored solution through a real run, exactly as a user would type it. */
    private static void everyLessonCanBeSolvedAtPar() {
        Set<String> ids = new HashSet<>();
        for (Lesson lesson : Lessons.ALL) {
            check(ids.add(lesson.id()), "duplicate lesson id " + lesson.id());
            for (int seed = 0; seed < 25; seed++) {
                Run run = new Run(lesson, new Random(seed));
                check(run.tasks().size() >= 6, lesson.id() + " has too few tasks");
                long now = 0;
                while (!run.finished()) {
                    Task task = run.task();
                    String keys = Keys.parse(task.solution());
                    check(keys.length() == task.par(), lesson.id() + ": par of '"
                            + task.solution() + "' is " + task.par());
                    check(!task.reached(run.vim()), lesson.id() + ": task starts already solved");
                    for (int i = 0; i < keys.length(); i++) {
                        boolean done = run.key(keys.charAt(i), now += 100);
                        check(done == (i == keys.length() - 1), lesson.id() + " task "
                                + (run.index() + 1) + " (seed " + seed + "): '" + task.solution()
                                + "' " + (done ? "finished early" : "did not reach the goal")
                                + ", buffer is:\n" + run.vim().text());
                    }
                    run.advance();
                }
                Attempt attempt = run.attempt(0);
                check(attempt.keys() == attempt.par() && attempt.efficiency() == 100,
                        lesson.id() + ": a par run scores 100%");
            }
        }
    }

    /** A drill's targets should be ones where the lesson's new keys are part of the answer. */
    private static void drillsTeachTheirKeys() {
        String[][] expected = {{"hjkl", "hjkl"}, {"words", "wbe"}, {"line", "0^$"},
                {"jumps", "gG23456789"}, {"find", "fFtT"}};
        for (String[] e : expected) {
            Lesson lesson = Lessons.ALL.stream().filter(l -> l.id().equals(e[0])).findFirst()
                    .orElseThrow();
            int using = 0;
            int total = 0;
            for (int seed = 0; seed < 25; seed++) {
                for (Task task : lesson.tasks().apply(new Random(seed))) {
                    total++;
                    if (task.solution().chars().anyMatch(c -> e[1].indexOf(c) >= 0)) {
                        using++;
                    }
                }
            }
            check(using >= total * 9 / 10, e[0] + ": only " + using + " of " + total
                    + " targets need the new keys");
        }
    }

    private static void vimBasics() {
        same("one two three", 0, 0, "dw", "two three", 0, 0);
        same("one two three", 0, 4, "cwTWO<esc>", "one TWO three", 0, 6);
        same("a\nb\nc", 1, 0, "ddp", "a\nc\nb", 2, 0);
        same("a\nb\nc", 0, 0, "ddu", "a\nb\nc", -1, -1);
        same("a\nb\nc", 0, 0, "ddu<c-r>", "b\nc", -1, -1);
        same("x = 1", 0, 0, "A;<esc>", "x = 1;", 0, 5);
        same("f(a, b)", 0, 3, "ci(x<esc>", "f(x)", 0, 2);
        same("say \"hi\" now", 0, 0, "di\"", "say \"\" now", 0, 5);
        same("one two", 0, 0, "vey$p", "one twoone", 0, 9);
        same("abc abc abc", 0, 0, "/abc<enter>n", "abc abc abc", 0, 8);
        same("one\ntwo\nthree", 0, 0, "Vjd", "three", 0, 0);
        same("one two three four", 0, 0, "dw..", "four", 0, 0);
        same("  indented", 0, 4, "ochild<esc>", "  indented\n  child", 1, 6);
        same("word", 0, 0, "ix<bs><bs>y<esc>", "yword", 0, 0);

        Vim vim = new Vim("only", 0, 0);
        vim.key('d');
        check(vim.pending().equals("d") && !vim.failed(), "d waits for a motion");
        vim.key('z');
        check(vim.pending().isEmpty() && vim.failed() && vim.text().equals("only"),
                "an unknown motion cancels the command");
    }

    private static void scoring() {
        Attempt attempt = new Attempt(1L, "words", 12.5, 40, 30, 8);
        check(attempt.efficiency() == 75, "efficiency is par over keys");
        check(new Attempt(1L, "words", 1, 20, 30, 8).efficiency() == 100, "capped at 100");
        check(Attempt.fromLine(attempt.toLine()).equals(attempt), "history line round trip");
    }

    private static void same(String text, int row, int col, String keys, String wantText,
                             int wantRow, int wantCol) {
        Vim vim = new Vim(text, row, col);
        for (char k : Keys.parse(keys).toCharArray()) {
            vim.key(k);
        }
        check(vim.text().equals(wantText), keys + " gave:\n" + vim.text());
        if (wantRow >= 0) {
            check(vim.row() == wantRow && vim.col() == wantCol, keys + " left the cursor at "
                    + vim.row() + "," + vim.col());
        }
    }

    private static void check(boolean condition, String what) {
        checks++;
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
