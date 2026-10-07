package vimdojo;

import java.awt.event.KeyEvent;
import javax.swing.JPanel;

/** Checks the keyboard navigation between screens. Runs headless; see ./test.sh */
public final class NavTest {
    private static final JPanel SOURCE = new JPanel();
    private static int checks;
    private static App app;

    public static void main(String[] args) {
        try {
            run();
            System.out.println("ok - " + checks + " navigation checks passed");
            System.exit(0);
        } catch (Throwable failure) {
            // Swing's timer thread would otherwise keep a failed run alive for ever.
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static void run() {
        app = new App();
        boolean[] quit = {false};
        app.quit = () -> quit[0] = true;

        tour();
        command("restart");
        locking();

        check(app.card().equals("challenge") && app.inIntro() && app.lessonIndex() == 0,
                "starts on the first lesson's introduction");

        type(":stats");
        check(":stats".equals(":" + app.command()), "the command line collects what is typed");
        check(app.card().equals("challenge"), "nothing happens until enter");
        press(KeyEvent.VK_ENTER);
        check(app.command() == null && app.card().equals("stats"), ":stats opens the statistics");

        command("lessons");
        check(app.card().equals("lessons"), ":lessons");
        command("settings");
        check(app.card().equals("settings"), ":settings");
        command("7");
        check(app.card().equals("challenge") && app.lessonIndex() == 6, ":7 opens lesson 7");
        command("next");
        check(app.lessonIndex() == 7, ":next");
        command("prev");
        command("p");
        check(app.lessonIndex() == 5, ":prev and :p");
        command("99");
        check(app.lessonIndex() == 5 && app.message().startsWith("E16"), "out-of-range lesson");
        command("1");
        command("prev");
        check(app.lessonIndex() == 0 && app.message().startsWith("E164"), "no lesson before 1");
        command("bogus");
        check(app.message().equals("E492: Not an editor command: bogus"), "unknown command");
        type("j");
        check(app.message() == null, "the next key clears the message");

        command("colo paper");
        check(Theme.current().name().equals("paper"), ":colo switches theme");
        command("colo nope");
        check(Theme.current().name().equals("paper") && app.message().startsWith("E185"),
                "unknown theme");
        command("colo ink");

        // gt and gT walk the screens in order and wrap around.
        String[] order = {"lessons", "stats", "settings", "challenge"};
        for (String screen : order) {
            type("gt");
            check(app.card().equals(screen), "gt reaches " + screen + ", got " + app.card());
        }
        type("gT");
        check(app.card().equals("settings"), "gT goes back");
        command("ready");
        check(app.card().equals("challenge"), ":ready returns to the lesson");
        command("stats");
        command("lesson");

        // Escape and an emptied line both abandon a command.
        type(":stats");
        press(KeyEvent.VK_ESCAPE);
        check(app.command() == null && app.card().equals("challenge"), "escape cancels");
        type(":s");
        press(KeyEvent.VK_BACK_SPACE);
        press(KeyEvent.VK_BACK_SPACE);
        check(app.command() == null, "backspacing past the colon cancels");

        // Mid-task, keys go to Vim unless Vim would hand them over.
        app.startLesson(6);
        check(!app.globalKey(typed('l')), "h and l on the intro card are left to the lesson view");
        app.execute("restart");
        check(app.inIntro(), ":restart returns to the introduction");

        dvorakLayout();
        settingsByMouse();
        demonstrations();
        lessonListScrolls();
        docs();
        replays();

        type(":q");
        press(KeyEvent.VK_ENTER);
        check(quit[0], ":q quits");

    }

    private static void tour() {
        check(app.guideOpen() && app.guideStep() == 0, "a first launch opens the tour");
        type(":stats");
        check(app.command() == null && app.card().equals("challenge"),
                "other keys are held back during the tour");
        java.util.Set<String> seen = new java.util.HashSet<>();
        int steps = 0;
        while (app.guideOpen()) {
            seen.add(app.docsOpen() ? "docs" : app.card());
            press(KeyEvent.VK_ENTER);
            steps++;
            check(steps < 50, "the tour ends");
        }
        check(seen.equals(java.util.Set.of("challenge", "lessons", "stats", "settings", "docs")),
                "the tour visits every screen, got " + seen);
        check(app.card().equals("challenge") && !app.inIntro() && !app.docsOpen(),
                "the end of the tour starts the first lesson");
        check(Settings.load().finishedGuide, "finishing the tour is remembered");
        App again = new App();
        check(!again.guideOpen(), "the tour isn't shown a second time");

        command("tour");
        check(app.guideOpen() && app.guideStep() == 0, ":tour shows it again");
        press(KeyEvent.VK_ENTER);
        press(KeyEvent.VK_ESCAPE);
        check(!app.guideOpen() && app.card().equals("challenge"), "escape skips the rest");
    }

    /**
     * Lessons unlock one at a time, each at 50% on the one before; a locked one can be looked at
     * but not begun, and erasing locks them again.
     */
    private static void locking() {
        ChallengeView view = null;
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>();
        queue.add(app.root());
        while (!queue.isEmpty()) {
            java.awt.Component c = queue.poll();
            if (c instanceof ChallengeView v) {
                view = v;
            } else if (c instanceof java.awt.Container k) {
                queue.addAll(java.util.List.of(k.getComponents()));
            }
        }
        int random = Lessons.indexOf("random");
        check(app.unlocked(0) && !app.unlocked(1) && !app.unlocked(random),
                "a new user starts with only lesson 1 unlocked");
        command("2");
        check(app.lessonIndex() == 1 && app.inIntro() && app.locked(),
                "a locked lesson still opens, to look at");
        pressOn(view, KeyEvent.VK_ENTER);
        check(app.inIntro() && app.message().startsWith("Lesson 2 is locked: reach 50%"),
                "but enter doesn't begin it: " + app.message());
        command(Integer.toString(random + 1));
        pressOn(view, KeyEvent.VK_ENTER);
        check(app.inIntro() && app.message().startsWith("The mixes are locked"),
                "nor a mix");
        command("1");
        check(!app.locked(), "lesson 1 is never locked");

        app.history().add(new Attempt(1L, "hjkl", 10, 100, 40, 8), java.util.List.of());
        check(!app.unlocked(1), "40% is not enough");
        app.history().add(new Attempt(2L, "hjkl", 10, 100, 50, 8), java.util.List.of());
        check(app.unlocked(1) && !app.unlocked(2) && app.unlocked(random),
                "50% unlocks lesson 2 and the mixes, and nothing further");
        command("2");
        pressOn(view, KeyEvent.VK_ENTER);
        check(app.lessonIndex() == 1 && !app.inIntro(), "lesson 2 can be played now");

        app.eraseProgress();
        check(!app.unlocked(1) && app.lessonIndex() == 0,
                "erasing locks the lessons again and goes back to lesson 1");

        // Unlock everything for the checks that follow.
        for (Lesson lesson : Lessons.LESSONS) {
            app.history().add(new Attempt(3L, lesson.id(), 10, 8, 8, 8), java.util.List.of());
        }
        for (int i = 0; i < Lessons.ALL.size(); i++) {
            check(app.unlocked(i), "passing every lesson unlocks " + Lessons.ALL.get(i).id());
        }
        command("1");
    }

    private static void dvorakLayout() {
        check(Layout.QWERTY.length() == Layout.DVORAK.length(), "layout tables line up");
        check(Layout.QWERTY.chars().sorted().boxed().toList()
                .equals(Layout.DVORAK.chars().sorted().boxed().toList()),
                "dvorak is a rearrangement of the same characters");
        check(Layout.dvorak('j') == 'h' && Layout.dvorak('c') == 'j' && Layout.dvorak('Z') == ':'
                && Layout.dvorak(' ') == ' ', "known positions");

        command("dvorak");
        check(app.dvorak() && app.message().startsWith("Keyboard layout: dvorak"), ":dvorak");
        // Commands and navigation are untouched, exactly as with Vim's keymap option.
        command("stats");
        check(app.card().equals("stats"), "commands are typed normally with dvorak on");
        type("gt");
        check(app.card().equals("settings"), "so is navigation");

        // Inside a lesson: commands keep their keys, typed text is converted.
        ChallengeView view = null;
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>();
        queue.add(app.root());
        while (!queue.isEmpty()) {
            java.awt.Component c = queue.poll();
            if (c instanceof ChallengeView v) {
                view = v;
            } else if (c instanceof java.awt.Container k) {
                queue.addAll(java.util.List.of(k.getComponents()));
            }
        }
        // The guided search lesson always uses the same text, and its first target is far away.
        app.startLesson(Lessons.indexOf("search"), true);
        pressOn(view, KeyEvent.VK_ENTER);
        typeOn(view, "jl");
        Vim vim = app.run().vim();
        check(vim.row() == 1 && vim.col() == 1, "j and l still move down and right");
        String before = vim.text();
        typeOn(view, "is");
        check(vim.mode() == Vim.Mode.INSERT && vim.text().length() == before.length() + 1
                && vim.lines().get(1).charAt(1) == 'o',
                "in insert mode the qwerty s key types dvorak o, got: " + vim.lines().get(1));
        pressOn(view, KeyEvent.VK_ESCAPE);
        typeOn(view, "u0fs");
        check(vim.text().equals(before) && vim.col() == vim.lines().get(1).indexOf('o', 1),
                "f takes its character in dvorak: fs finds an o");
        typeOn(view, "/;");
        check(vim.searchText().equals("s"), "search patterns are typed in dvorak");
        pressOn(view, KeyEvent.VK_ESCAPE);

        // h on the first lesson and l on the last have nowhere to go and must not restart it.
        app.startLesson(0);
        Run firstRun = app.run();
        typeOn(view, "h");
        check(app.run() == firstRun && app.lessonIndex() == 0, "h on the first lesson does nothing");
        typeOn(view, "l");
        check(app.lessonIndex() == 1 && app.run() != firstRun, "l still moves on");
        typeOn(view, "h");
        check(app.lessonIndex() == 0, "h still moves back");
        app.startLesson(Lessons.ALL.size() - 1);
        Run lastRun = app.run();
        typeOn(view, "l");
        check(app.run() == lastRun, "l on the last lesson does nothing");

        command("qwerty");
        check(!app.dvorak() && app.message().equals("Keyboard layout: qwerty."), ":qwerty");
        command("set keymap=dvorak");
        check(app.dvorak(), "vim's own :set keymap=dvorak turns it on");
        command("set keymap=");
        check(!app.dvorak(), ":set keymap= turns it off");
        command("set nonsense");
        check(app.message().startsWith("E518"), "unknown option");
        command("lesson");
        check(app.card().equals("challenge"), "back in the lesson");
    }

    private static void typeOn(ChallengeView view, String text) {
        for (char c : text.toCharArray()) {
            KeyEvent e = typed(c);
            if (!app.globalKey(e)) {
                for (java.awt.event.KeyListener l : view.getKeyListeners()) {
                    l.keyTyped(e);
                }
            }
        }
    }

    private static void pressOn(ChallengeView view, int code) {
        KeyEvent e = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, code,
                KeyEvent.CHAR_UNDEFINED);
        if (!app.globalKey(e)) {
            for (java.awt.event.KeyListener l : view.getKeyListeners()) {
                l.keyPressed(e);
            }
        }
    }

    /** Paints the settings screen, then clicks where each choice was drawn. */
    private static void settingsByMouse() {
        command("settings");
        SettingsView view = null;
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>();
        queue.add(app.root());
        while (!queue.isEmpty()) {
            java.awt.Component c = queue.poll();
            if (c instanceof SettingsView v) {
                view = v;
            } else if (c instanceof java.awt.Container k) {
                queue.addAll(java.util.List.of(k.getComponents()));
            }
        }
        view.setSize(1180, 600);
        java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(1180, 600,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        view.paint(g);
        // The same geometry the screen uses: choices start 220 px into a 920 px column.
        int left = (1180 - 920) / 2;
        java.awt.FontMetrics plain = g.getFontMetrics(Theme.ui(15f));
        java.awt.FontMetrics bold = g.getFontMetrics(Theme.bold(15f));
        int themeY = 12 + 24 + 18;
        int x = left + 220;
        for (Theme theme : Theme.ALL) {
            boolean current = theme == Theme.current();
            int w = (current ? bold : plain).stringWidth(theme.name());
            if (theme.name().equals("moss")) {
                click(view, x + w / 2, themeY);
            }
            x += w + 28;
        }
        check(Theme.current().name().equals("moss"), "clicking a theme name selects it, got "
                + Theme.current().name());
        view.paint(g);
        int layoutY = themeY + 42;
        int dvorakX = left + 220 + bold.stringWidth("qwerty") + 28
                + plain.stringWidth("dvorak") / 2;
        click(view, dvorakX, layoutY);
        check(app.dvorak(), "clicking dvorak selects it");
        view.paint(g);
        click(view, left + 220 + plain.stringWidth("qwerty") / 2, layoutY);
        check(!app.dvorak(), "clicking qwerty selects it");
        app.setTheme("ink");
        g.dispose();
    }

    /** Steps every lesson's demonstration far enough to loop, painting as it goes. */
    private static void demonstrations() {
        int recorded = app.history().all().size();
        ChallengeView view = null;
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>();
        queue.add(app.root());
        while (!queue.isEmpty()) {
            java.awt.Component c = queue.poll();
            if (c instanceof ChallengeView v) {
                view = v;
            } else if (c instanceof java.awt.Container k) {
                queue.addAll(java.util.List.of(k.getComponents()));
            }
        }
        view.setSize(1180, 640);
        java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(1180, 640,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        for (int lesson = 0; lesson < Lessons.ALL.size(); lesson++) {
            app.startLesson(lesson);
            for (int step = 0; step < 400; step++) {
                view.demoStep();
                if (step % 7 == 0) {
                    view.paint(g);
                }
            }
            check(app.inIntro() && app.run().index() == 0,
                    "the demonstration leaves the real lesson untouched");
        }
        check(app.history().all().size() == recorded, "demonstrations are never recorded");
        g.dispose();
    }

    /** In a window too short for all the lessons, the wheel scrolls and clicks still land. */
    private static void lessonListScrolls() {
        command("lessons");
        LessonsView view = null;
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>();
        queue.add(app.root());
        while (!queue.isEmpty()) {
            java.awt.Component c = queue.poll();
            if (c instanceof LessonsView v) {
                view = v;
            } else if (c instanceof java.awt.Container k) {
                queue.addAll(java.util.List.of(k.getComponents()));
            }
        }
        view.setSize(1180, 300);
        // Open the list with lesson 1 selected, so it starts scrolled to the top.
        command("1");
        command("lessons");
        // The first row sits at y 44 and rows are 34 tall; scroll down by three rows.
        java.awt.event.MouseWheelEvent wheel = new java.awt.event.MouseWheelEvent(view,
                java.awt.event.MouseEvent.MOUSE_WHEEL, 0, 0, 500, 100, 0, false,
                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 3);
        for (java.awt.event.MouseWheelListener l : view.getMouseWheelListeners()) {
            l.mouseWheelMoved(wheel);
        }
        click(view, 500, 44 + 10);
        check(app.card().equals("challenge") && app.lessonIndex() == 3,
                "after scrolling three rows the top row is lesson 4, got lesson "
                        + (app.lessonIndex() + 1));

        // Scrolling can't run past either end.
        command("lessons");
        java.awt.event.MouseWheelEvent far = new java.awt.event.MouseWheelEvent(view,
                java.awt.event.MouseEvent.MOUSE_WHEEL, 0, 0, 500, 100, 0, false,
                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 500);
        for (java.awt.event.MouseWheelListener l : view.getMouseWheelListeners()) {
            l.mouseWheelMoved(far);
        }
        int lastRowY = 300 - 8 - 34 + 10;
        click(view, 500, lastRowY);
        check(app.lessonIndex() == Lessons.ALL.size() - 1,
                "scrolled to the end, the bottom row is the last lesson, got lesson "
                        + (app.lessonIndex() + 1));
        command("1");
    }

    private static void docs() {
        // Every lesson key is documented, and every example plays cleanly from start to end.
        int lessonKeys = Lessons.ALL.stream().mapToInt(l -> l.keys().size()).sum();
        check(Docs.ALL.stream().filter(e -> e.hasDemo() && !e.group().equals("more vim keys"))
                .count() == lessonKeys, "one example per lesson key");
        check(Docs.ALL.stream().filter(e -> e.group().equals("more vim keys"))
                .allMatch(Docs.Entry::hasDemo), "every extra vim key has an example");
        // Every command the app accepts is written down somewhere in the docs.
        String everything = Docs.ALL.stream().map(e -> e.key() + " " + String.join(" ", e.more()))
                .collect(java.util.stream.Collectors.joining(" "));
        for (String word : new String[] {"docs", "doc", "lessons", "ls", "stats", "settings",
                "set", "help", "h", "lesson", "l", "ready", "restart", "e", "e!", "next", "n",
                "prev", "previous", "p", "N", "colorscheme", "colo", "color", "theme", "dvorak",
                "qwerty", "q", "q!", "quit", "qa", "wq", "x"}) {
            check(everything.contains(":" + word + " ") || everything.contains(":" + word + "`")
                    || everything.endsWith(":" + word), "the docs don't mention :" + word);
        }
        command("color moss");
        check(Theme.current().name().equals("moss"), ":color works like :colo");
        command("theme ink");
        for (Docs.Entry entry : Docs.ALL) {
            if (!entry.hasDemo()) {
                continue;
            }
            Vim vim = entry.vim();
            String before = vim.text() + vim.row() + "," + vim.col();
            for (char k : Keys.parse(entry.keys()).toCharArray()) {
                vim.key(k);
                check(!vim.failed(), "the example for " + entry.key() + " has a key that fails");
            }
            check(vim.mode() == Vim.Mode.NORMAL && vim.pending().isEmpty(),
                    "the example for " + entry.key() + " ends mid-command");
            boolean undoes = entry.keys().endsWith("u");
            check(undoes || !before.equals(vim.text() + vim.row() + "," + vim.col()),
                    "the example for " + entry.key() + " shows nothing happening");
        }

        // It opens over any screen, including mid-lesson, and gives the screen back on closing.
        command("1");
        check(!app.docsOpen(), "closed to begin with");
        command("docs");
        check(app.docsOpen() && app.card().equals("challenge"),
                ":docs opens over the current screen");
        type("G");
        type("gg");
        app.closeDocs();
        check(!app.docsOpen() && app.card().equals("challenge") && app.inIntro(),
                "closing returns to the same screen");
        command("docs");
        command("stats");
        check(!app.docsOpen() && app.card().equals("stats"), "going elsewhere closes the docs");
        app.toggleDocs();
        app.toggleDocs();
        check(!app.docsOpen(), "the docs link toggles");

        // Read like a manual page: :q leaves the docs, not the app, and / searches them.
        boolean[] quit = {false};
        Runnable realQuit = app.quit;
        app.quit = () -> quit[0] = true;
        command("docs");
        command("q");
        check(!app.docsOpen() && !quit[0], ":q in the docs only closes the docs");
        command("q");
        check(quit[0], ":q outside the docs quits");
        app.quit = realQuit;

        command("docs");
        type("gg");
        type("/THEME");
        check(app.prompt() == '/' && "THEME".equals(app.command()), "/ opens a search prompt");
        press(KeyEvent.VK_ENTER);
        check(app.docsOpen() && app.message() == null, "the search found something");
        String first = app.docsStatus();
        check(first.contains("/THEME 1 of "), "status after searching: " + first);
        // Searching ignores case and looks in the notes as well as the names.
        int total = Integer.parseInt(first.substring(first.lastIndexOf(' ') + 1));
        check(total >= 2, "theme is mentioned in more than one entry");
        typeOnDocs("n");
        check(app.docsStatus().contains("/THEME 2 of "), "n goes to the next match");
        typeOnDocs("N");
        check(app.docsStatus().equals(first), "N goes back");
        for (int i = 0; i < total; i++) {
            typeOnDocs("n");
        }
        check(app.docsStatus().equals(first), "n wraps around to the first match");

        type("/zzzz");
        press(KeyEvent.VK_ENTER);
        check(app.message().equals("E486: Pattern not found: zzzz")
                && app.docsStatus().equals(first), "a failed search changes nothing");
        type("/x");
        press(KeyEvent.VK_ESCAPE);
        check(app.command() == null && app.docsStatus().equals(first), "esc abandons a search");
        app.closeDocs();
        check(!app.globalKey(typed('/')) && app.command() == null,
                "outside the docs / is not a search");
        command("1");
    }

    /** Finishes each lesson, then replays every task from the results screen. */
    private static void replays() {
        ReplayView replay = null;
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>();
        queue.add(app.root());
        while (!queue.isEmpty()) {
            java.awt.Component c = queue.poll();
            if (c instanceof ReplayView v) {
                replay = v;
            } else if (c instanceof java.awt.Container k) {
                queue.addAll(java.util.List.of(k.getComponents()));
            }
        }
        java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(600, 600,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        int before = app.history().all().size();
        for (int lesson = 0; lesson < Lessons.ALL.size(); lesson++) {
            app.startLesson(lesson);
            app.replay(0);
            check(!app.replayOpen(), "no replays before a lesson is finished");
            Run run = app.run();
            long now = 0;
            while (!run.finished()) {
                // One wasted key, then par, so what was typed differs from the solution.
                run.key(Vim.ESC, now += 100);
                String keys = Keys.parse(run.task().solution());
                for (char k : keys.toCharArray()) {
                    run.key(k, now += 100);
                }
                check(Keys.parse(Keys.notation(run.typed(run.index()))).equals(Vim.ESC + keys),
                        "the keys pressed are remembered exactly");
                run.advance();
            }
            app.finishRun();
            for (int task = 0; task < run.tasks().size(); task++) {
                app.replay(task);
                check(app.replayOpen() && app.replayTask() == task, "replay opens on the task");
                replay.setSize(replay.getPreferredSize());
                // Step to the end of the solution: the replay must arrive where par did.
                Task t = run.tasks().get(task);
                int presses = Keys.parse(t.solution()).length();
                for (int i = 0; i < presses; i++) {
                    replay.step();
                    replay.paint(g);
                }
                check(replayReached(replay, t), Lessons.ALL.get(lesson).id() + " task "
                        + (task + 1) + ": the replay of '" + t.solution() + "' misses the goal");
                replay.step();
            }
            command("q");
            check(!app.replayOpen() && app.card().equals("result"),
                    ":q closes the replay and stays on the results");
        }
        app.replay(2);
        app.closeReplay();
        check(!app.replayOpen(), "closing");
        app.replay(1);
        command("stats");
        check(!app.replayOpen(), "going elsewhere closes a replay");
        // These practice runs shouldn't linger in the history the other checks look at.
        check(app.history().all().size() == before + Lessons.ALL.size(), "one run per lesson");
        app.history().clear();
        g.dispose();
        command("1");
    }

    private static boolean replayReached(ReplayView replay, Task task) {
        try {
            java.lang.reflect.Field field = ReplayView.class.getDeclaredField("vim");
            field.setAccessible(true);
            return task.reached((Vim) field.get(replay));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void typeOnDocs(String text) {
        DocsView view = null;
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>();
        queue.add(app.root());
        while (!queue.isEmpty()) {
            java.awt.Component c = queue.poll();
            if (c instanceof DocsView v) {
                view = v;
            } else if (c instanceof java.awt.Container k) {
                queue.addAll(java.util.List.of(k.getComponents()));
            }
        }
        for (char c : text.toCharArray()) {
            KeyEvent e = typed(c);
            if (!app.globalKey(e)) {
                for (java.awt.event.KeyListener l : view.getKeyListeners()) {
                    l.keyTyped(e);
                }
            }
        }
    }

    private static void click(java.awt.Component view, int x, int y) {
        java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(view,
                java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false,
                java.awt.event.MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener l : view.getMouseListeners()) {
            l.mousePressed(e);
        }
    }

    private static void command(String text) {
        type(":" + text);
        press(KeyEvent.VK_ENTER);
    }

    private static void type(String text) {
        for (char c : text.toCharArray()) {
            app.globalKey(typed(c));
        }
    }

    private static KeyEvent typed(char c) {
        return new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c);
    }

    private static void press(int code) {
        app.globalKey(new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, code,
                KeyEvent.CHAR_UNDEFINED));
    }

    private static void check(boolean condition, String what) {
        checks++;
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
