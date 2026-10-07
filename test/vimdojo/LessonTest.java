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
        editingTasksVary();
        vimBasics();
        scoring();
        activity();
        System.out.println("ok - " + checks + " checks passed");
    }

    /** Plays each task's stored solution through a real run, exactly as a user would type it. */
    private static void everyLessonCanBeSolvedAtPar() {
        Set<String> ids = new HashSet<>();
        for (Lesson lesson : Lessons.ALL) {
            check(ids.add(lesson.id()), "duplicate lesson id " + lesson.id());
            for (int seed = 0; seed < 300; seed++) {
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

    /** Editing lessons should not be the same six tasks every time. */
    private static void editingTasksVary() {
        for (Lesson lesson : Lessons.ALL) {
            if (List.of("hjkl", "words", "line", "jumps", "find", "search").contains(lesson.id())) {
                continue;
            }
            Set<String> seen = new HashSet<>();
            for (int seed = 0; seed < 40; seed++) {
                for (Task task : lesson.tasks().apply(new Random(seed))) {
                    seen.add(task.start() + "/" + task.row() + "," + task.col());
                    check(!task.start().contains("|") && !task.goal().contains("|"),
                            lesson.id() + ": stray cursor marker in " + task.start());
                }
            }
            check(seen.size() >= 60, lesson.id() + ": only " + seen.size()
                    + " different tasks in 40 runs");
        }
        Lesson review = Lessons.ALL.get(Lessons.ALL.size() - 1);
        List<Task> tasks = review.tasks().apply(new Random(7));
        check(review.id().equals("review") && tasks.size() == 10, "the review has ten tasks");
        check(tasks.stream().allMatch(t -> t.prompt().equals("make the text match the goal")),
                "review prompts give nothing away");
        check(tasks.stream().map(Task::solution).distinct().count() >= 8,
                "review tasks come from different templates");
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

        Vim searching = new Vim("abc abc\nabc", 0, 0);
        check(searching.highlight().isEmpty() && searching.searchPreview() == null,
                "nothing is highlighted before a search");
        searching.key('/');
        searching.key('a');
        searching.key('b');
        check(searching.highlight().equals("ab"), "matches light up while the search is typed");
        check(searching.searchPreview()[0] == 0 && searching.searchPreview()[1] == 4,
                "the preview is the match enter would jump to");
        check(searching.row() == 0 && searching.col() == 0, "previewing doesn't move the cursor");
        searching.key(Vim.ENTER);
        check(searching.col() == 4 && searching.highlight().equals("ab")
                && searching.searchPreview() == null, "matches stay lit after enter");
        searching.key('/');
        check(searching.highlight().isEmpty(), "a new search starts with nothing lit");
        searching.key(Vim.ESC);
        check(searching.highlight().equals("ab"), "canceling restores the last search");

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
        check(new Attempt(1L, "words", 1, 20, 30, 8).efficiency() == 150,
                "beating par scores above 100");
        check(Attempt.fromLine(attempt.toLine()).equals(attempt), "history line round trip");
    }

    private static void activity() {
        java.time.ZoneId utc = java.time.ZoneOffset.UTC;
        java.time.LocalDate today = java.time.LocalDate.of(2026, 3, 10);
        // Days before today on which lessons were finished: a 3-day run, a gap, then a 2-day run
        // reaching yesterday, with two lessons on one of those days.
        int[] daysAgo = {9, 8, 7, 2, 2, 1};
        List<Attempt> attempts = new java.util.ArrayList<>();
        for (int ago : daysAgo) {
            attempts.add(new Attempt(today.minusDays(ago).atTime(20, 0).toInstant(
                    java.time.ZoneOffset.UTC).toEpochMilli(), "hjkl", 10, 20, 20, 8));
        }
        Activity before = Activity.of(attempts, today, utc);
        check(before.on(today.minusDays(2)) == 2 && before.on(today) == 0, "lessons per day");
        check(before.currentStreak() == 2 && before.longestStreak() == 3,
                "a streak that reaches yesterday is still alive");
        check(!before.practicedToday() && before.nudge().contains("keep your 2 days"),
                "nudge before practicing: " + before.nudge());

        attempts.add(new Attempt(today.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli(), "hjkl", 10, 20, 20, 8));
        Activity after = Activity.of(attempts, today, utc);
        check(after.currentStreak() == 3 && after.practicedToday()
                && after.nudge().contains("3 days in a row"), "today extends the streak");
        check(Activity.of(attempts, today.plusDays(2), utc).currentStreak() == 0,
                "missing a whole day ends it");
        check(Activity.of(List.of(), today, utc).nudge().contains("start a streak"),
                "empty history");
        // The same instant falls on different days in different time zones.
        long lateEvening = today.atTime(23, 30).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        List<Attempt> one = List.of(new Attempt(lateEvening, "hjkl", 10, 20, 20, 8));
        check(Activity.of(one, today, java.time.ZoneOffset.ofHours(2)).on(today.plusDays(1)) == 1,
                "days follow the local clock");
        check(Activity.level(0) == 0 && Activity.level(1) == 1 && Activity.level(5) == 3
                && Activity.level(40) == 4, "shade levels");
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
