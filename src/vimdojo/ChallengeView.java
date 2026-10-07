package vimdojo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The lesson itself. First an introduction to the new keys, then one task at a time: a prompt,
 * the buffer in its own panel, and for editing tasks the text to aim for.
 */
final class ChallengeView extends JComponent {
    private static final float FONT_SIZE = 22f;
    private static final int LINE_HEIGHT = 34;
    private static final int PAD = 22;
    private static final int REMINDER_GAP = 24;
    private static final int REMINDER_ROW = 28;
    // The introduction card's layout is the same for every lesson.
    private static final int INTRO_WIDTH = 540;
    private static final int INTRO_KEY_COLUMN = 170;
    private static final int INTRO_ROWS = 5;
    private static final int INTRO_ROW_HEIGHT = 46;
    private static final int PAUSE_AFTER_TASK_MS = 450;
    private static final int DEMO_WIDTH = 440;
    // Room the demonstration takes when it sits under the keys: label, task and the key strip.
    private static final int STACKED_DEMO_HEIGHT = 304;
    private static final int DEMO_PAUSE_MS = 1100;

    private final App app;
    private boolean intro = true;
    // The demonstration on the introduction card: a run of the same lesson that plays itself.
    private final List<String> demoTyped = new ArrayList<>();
    private Run demo;
    private int introScroll;
    private int introOverflow;
    private int demoKey;
    private long demoNextAt;
    // Where the introduction card last put its keys and its demonstration, for the tour.
    private final Rectangle introKeys = new Rectangle();
    private final Rectangle introDemo = new Rectangle();

    ChallengeView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_TAB -> app.startLesson(app.lessonIndex());
                    case KeyEvent.VK_ESCAPE -> feed(Vim.ESC);
                    case KeyEvent.VK_BACK_SPACE -> feed(Vim.BACKSPACE);
                    case KeyEvent.VK_ENTER -> {
                        if (intro) {
                            start();
                        } else {
                            feed(Vim.ENTER);
                        }
                    }
                    case KeyEvent.VK_R -> {
                        if (e.isControlDown()) {
                            feed(Vim.CTRL_R);
                        }
                    }
                    default -> {
                    }
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (intro && (c == 'h' || c == 'l')) {
                    // Browse the lessons from the introduction card. At either end of the
                    // list there is nowhere to go, so the key does nothing.
                    int target = app.lessonIndex() + (c == 'l' ? 1 : -1);
                    if (target >= 0 && target < Lessons.ALL.size()) {
                        app.startLesson(target);
                    }
                } else if (c >= 32 && c != 127 && !e.isControlDown() && !e.isMetaDown()) {
                    feed(c);
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });
        addMouseWheelListener(e -> {
            if (intro) {
                introScroll = Math.max(0, Math.min(introOverflow,
                        introScroll + (int) Math.round(e.getPreciseWheelRotation() * 30)));
                repaint();
            }
        });
        new Timer(50, e -> {
            if (isShowing() && intro && demo != null && now() >= demoNextAt) {
                demoStep();
                repaint();
            }
        }).start();
    }

    private static long now() {
        return System.nanoTime() / 1_000_000;
    }

    boolean inIntro() {
        return intro;
    }

    /** Leaves the introduction card for the first task. */
    void start() {
        if (intro) {
            intro = false;
            app.run().shown(now());
            app.refresh();
        }
    }

    Rectangle introKeys() {
        return new Rectangle(introKeys);
    }

    Rectangle introDemo() {
        return new Rectangle(introDemo);
    }

    /** Show the introduction for the run the app has just created. */
    void begin() {
        intro = true;
        introScroll = 0;
        startDemo();
        repaint();
    }

    private void feed(char c) {
        Run run = app.run();
        if (intro) {
            return;
        }
        if (app.dvorak() && c >= 32 && typingText(run.vim())) {
            c = Layout.dvorak(c);
        }
        if (run.key(c, now())) {
            // Leave the finished task on screen for a moment before moving on.
            Timer next = new Timer(PAUSE_AFTER_TASK_MS, e -> {
                if (app.run() != run) {
                    return;
                }
                run.advance();
                run.shown(now());
                if (run.finished()) {
                    app.finishRun();
                }
                app.refresh();
            });
            next.setRepeats(false);
            next.start();
        }
        app.refresh();
    }

    /**
     * Whether the next key is text and not a command. Like Vim's keymap option, Dvorak typing
     * covers inserted text, search patterns and the character given to f, t and r, and leaves
     * normal-mode commands on the keys they have always had.
     */
    private static boolean typingText(Vim vim) {
        if (vim.mode() == Vim.Mode.INSERT || vim.mode() == Vim.Mode.SEARCH) {
            return true;
        }
        String pending = vim.pending();
        return !pending.isEmpty() && "fFtTr".indexOf(pending.charAt(pending.length() - 1)) >= 0;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        if (intro) {
            paintIntro(g);
        } else {
            paintTask(g);
        }
    }

    private void paintIntro(Graphics2D g) {
        Theme t = Theme.current();
        Lesson lesson = app.run().lesson();
        // Everything hangs off one fixed corner, sized for the longest lesson, so the card
        // sits in the same place whichever lesson is showing. With room, the demonstration goes
        // beside the keys; in a narrow window it goes underneath them.
        boolean wide = getWidth() >= INTRO_WIDTH + DEMO_WIDTH + 80;
        introDemo.setBounds(0, 0, 0, 0);
        int rowHeight = wide ? INTRO_ROW_HEIGHT : 40;
        int keysTop = wide ? 136 : 124;
        int textHeight = keysTop + INTRO_ROWS * rowHeight;
        int left;
        int top;
        if (wide) {
            left = (getWidth() - INTRO_WIDTH - DEMO_WIDTH) / 2;
            top = Math.max(24, (getHeight() - (150 + INTRO_ROWS * INTRO_ROW_HEIGHT)) / 2 - 10);
            introOverflow = 0;
            if (demo != null) {
                paintDemo(g, left + INTRO_WIDTH + 20, top, getHeight() - top - 124);
            }
        } else {
            int total = textHeight + (demo == null ? 0 : STACKED_DEMO_HEIGHT);
            left = Math.max(20, (getWidth() - INTRO_WIDTH) / 2);
            // Too short for both: the card scrolls with the mouse wheel.
            introOverflow = Math.max(0, total + 8 - getHeight());
            introScroll = Math.min(introScroll, introOverflow);
            top = introOverflow > 0 ? 4 - introScroll : (getHeight() - total) / 2;
            if (demo != null) {
                paintDemo(g, left, top + textHeight, STACKED_DEMO_HEIGHT - 104);
            }
        }

        Paint.label(g, "lesson " + (app.lessonIndex() + 1) + " of " + Lessons.ALL.size(), left,
                top + 12);
        g.setFont(Theme.bold(36f));
        g.setColor(t.text());
        g.drawString(lesson.title(), left - 2, top + 58);
        g.setColor(t.accent());
        g.fillRect(left, top + 78, 44, 4);

        int y = top + keysTop;
        if (lesson.note() != null) {
            // Wrap the note to the card's width.
            g.setFont(Theme.ui(17f));
            g.setColor(t.text());
            StringBuilder line = new StringBuilder();
            for (String word : lesson.note().split(" ")) {
                if (g.getFontMetrics().stringWidth(line + " " + word) > INTRO_WIDTH - 40) {
                    g.drawString(line.toString(), left, y);
                    y += 30;
                    line.setLength(0);
                }
                line.append(line.isEmpty() ? "" : " ").append(word);
            }
            g.drawString(line.toString(), left, y);
        }
        for (Lesson.Key key : lesson.keys()) {
            Paint.keycap(g, key.key(), left, y, 19f);
            g.setFont(Theme.ui(17f));
            g.setColor(t.text());
            g.drawString(key.does(), left + INTRO_KEY_COLUMN, y);
            y += rowHeight;
        }
        // y is now one row past the last key, or on the note's last line if there are no keys.
        int bottom = (lesson.keys().isEmpty() ? y : y - rowHeight) + 16;
        introKeys.setBounds(left, top - 4, INTRO_WIDTH - 40, bottom - (top - 4));
    }

    /**
     * Width and height of a run's current task as {@link #paintRun} draws it. Room is reserved
     * for the taller of the start and goal texts, so the layout doesn't jump around while lines
     * are added and removed.
     */
    private int[] runSize(Graphics2D g, Run run) {
        Task task = run.task();
        String[] start = task.start().split("\n", -1);
        String[] goal = task.isMotion() ? new String[0] : task.goal().split("\n", -1);
        int rows = Math.max(run.vim().lines().size(), Math.max(start.length, goal.length));
        int charW = g.getFontMetrics(Theme.mono(FONT_SIZE)).charWidth('m');
        int widest = 0;
        for (String line : run.vim().lines()) {
            widest = Math.max(widest, line.length() + 1);
        }
        for (String line : start) {
            widest = Math.max(widest, line.length() + 1);
        }
        for (String line : goal) {
            widest = Math.max(widest, line.length() + 1);
        }
        int editorHeight = rows * LINE_HEIGHT + PAD * 2 - 6;
        int goalHeight = goal.length == 0 ? 0 : goal.length * LINE_HEIGHT + PAD * 2 + 12;
        return new int[] {Math.max(charW * 3 + widest * charW + PAD * 2, 540),
                78 + editorHeight + (goal.length == 0 ? 0 : 14 + goalHeight), editorHeight,
                goalHeight};
    }

    /** Progress dots, prompt, the buffer in its panel, and the goal: one task of a run. */
    private void paintRun(Graphics2D g, Run run, int left, int top) {
        Theme t = Theme.current();
        Task task = run.task();
        Vim vim = run.vim();
        List<String> lines = vim.lines();
        String[] goal = task.isMotion() ? new String[0] : task.goal().split("\n", -1);
        int[] size = runSize(g, run);
        int width = size[0];
        int editorHeight = size[2];
        int goalHeight = size[3];
        g.setFont(Theme.mono(FONT_SIZE));
        FontMetrics fm = g.getFontMetrics();
        int charW = fm.charWidth('m');
        int gutter = charW * 3;

        // Progress: one dot per task.
        int tasks = run.tasks().size();
        for (int i = 0; i < tasks; i++) {
            int dx = left + i * 18;
            if (i < run.index() || (i == run.index() && run.waiting())) {
                g.setColor(t.accent());
                g.fillOval(dx, top, 10, 10);
            } else if (i == run.index()) {
                g.setColor(t.accent());
                g.setStroke(new BasicStroke(2f));
                g.drawOval(dx + 1, top + 1, 8, 8);
            } else {
                g.setColor(t.panel());
                g.fillOval(dx, top, 10, 10);
            }
        }
        Paint.prose(g, task.prompt(), left, top + 52, 18f, t.text());

        // The buffer.
        boolean done = run.waiting();
        int editorTop = top + 78;
        Paint.panel(g, left, editorTop, width, editorHeight);
        if (done) {
            g.setColor(t.accent());
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(left, editorTop, width, editorHeight, 16, 16);
        }
        int textLeft = left + PAD + gutter;
        int y = editorTop + PAD - 4;
        Paint.buffer(g, vim, textLeft, y, FONT_SIZE, LINE_HEIGHT,
                task.isMotion() ? task.goalRow() : -1, task.goalCol());

        if (goal.length > 0) {
            int goalTop = editorTop + editorHeight + 14;
            g.setColor(t.panel());
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(left, goalTop, width, goalHeight, 16, 16);
            Paint.label(g, "goal", left + PAD, goalTop + 24);
            g.setFont(Theme.mono(FONT_SIZE));
            g.setColor(t.sub());
            for (int r = 0; r < goal.length; r++) {
                g.drawString(goal[r], textLeft, goalTop + 34 + r * LINE_HEIGHT + fm.getAscent());
            }
        }
    }

    /**
     * A small copy of a real task playing itself: the lesson's own tasks, solved one key at a
     * time, with the keys shown underneath as they are pressed.
     */
    private void paintDemo(Graphics2D g, int x, int top, int roomForTask) {
        Theme t = Theme.current();
        Paint.label(g, "demonstration", x, top + 12);
        int[] size = runSize(g, demo);
        int bodyTop = top + 34;
        double scale = Math.min(0.74, Math.min((double) (DEMO_WIDTH - 20) / size[0],
                (double) roomForTask / size[1]));
        Graphics2D small = (Graphics2D) g.create();
        small.translate(x, bodyTop);
        small.scale(scale, scale);
        paintRun(small, demo, 0, 0);
        small.dispose();

        // The keys pressed so far for this task, the newest one outlined.
        int keyX = x;
        int keyY = bodyTop + (int) (size[1] * scale) + 34;
        introDemo.setBounds(x, top - 4, DEMO_WIDTH - 20, keyY + 12 - (top - 4));
        for (int i = 0; i < demoTyped.size(); i++) {
            String name = demoTyped.get(i);
            int keyWidth = Paint.keycapWidth(g, name, 12.5f);
            if (keyX + keyWidth > x + DEMO_WIDTH - 20) {
                keyX = x;
                keyY += 30;
            }
            Paint.keycap(g, name, keyX, keyY, 12.5f);
            if (i == demoTyped.size() - 1) {
                g.setColor(t.accent());
                g.setStroke(new BasicStroke(1.6f));
                g.drawRoundRect(keyX - 2, keyY - 16, keyWidth + 4, 23, 8, 8);
            }
            keyX += keyWidth + 6;
        }
    }

    private void startDemo() {
        Lesson lesson = app.run().lesson();
        // The review gives no hints, so it has nothing to demonstrate.
        demo = lesson.keys().isEmpty() ? null : new Run(lesson, new Random());
        demoKey = 0;
        demoTyped.clear();
        demoNextAt = now() + DEMO_PAUSE_MS;
    }

    /** Advances the demonstration by one key, or on to its next task. */
    void demoStep() {
        if (demo == null) {
            return;
        }
        if (demo.waiting()) {
            demo.advance();
            demoKey = 0;
            demoTyped.clear();
            if (demo.finished()) {
                demo = new Run(demo.lesson(), new Random());
            }
            demoNextAt = now() + DEMO_PAUSE_MS;
            return;
        }
        String keys = Keys.parse(demo.task().solution());
        if (demoKey >= keys.length()) {
            startDemo();
            return;
        }
        char key = keys.charAt(demoKey++);
        demoTyped.add(switch (key) {
            case Vim.ESC -> "esc";
            case Vim.ENTER -> "enter";
            case Vim.BACKSPACE -> "bksp";
            case Vim.CTRL_R -> "ctrl-r";
            case ' ' -> "space";
            default -> String.valueOf(key);
        });
        demo.key(key, now());
        // Typing in insert mode goes by quickly; commands are held long enough to follow.
        boolean typing = demo.vim().mode() == Vim.Mode.INSERT
                || demo.vim().mode() == Vim.Mode.SEARCH;
        demoNextAt = now() + (demo.waiting() ? DEMO_PAUSE_MS + 500 : typing ? 230 : 650);
    }

    private void paintTask(Graphics2D g) {
        Theme t = Theme.current();
        Run run = app.run();

        // Reminder of this lesson's keys along the bottom, wrapped onto as many lines as the
        // window's width needs.
        List<List<String>> rows = new ArrayList<>();
        List<Integer> rowWidths = new ArrayList<>();
        for (Lesson.Key key : run.lesson().keys()) {
            String item = "`" + key.key() + "` " + key.does();
            int width = Paint.proseWidth(g, item, 13f);
            int last = rows.size() - 1;
            if (last < 0 || rowWidths.get(last) + REMINDER_GAP + width > getWidth() - 40) {
                rows.add(new ArrayList<>());
                rowWidths.add(-REMINDER_GAP);
                last++;
            }
            rows.get(last).add(item);
            rowWidths.set(last, rowWidths.get(last) + REMINDER_GAP + width);
        }
        int reminderHeight = rows.size() * REMINDER_ROW + 16;
        for (int r = 0; r < rows.size(); r++) {
            int x = (getWidth() - rowWidths.get(r)) / 2;
            int baseline = getHeight() - 22 - (rows.size() - 1 - r) * REMINDER_ROW;
            for (String item : rows.get(r)) {
                x += Paint.prose(g, item, x, baseline, 13f, t.sub()) + REMINDER_GAP;
            }
        }

        // The task itself, centered in what is left and shrunk if the window is too small for it.
        int[] size = runSize(g, run);
        int roomW = getWidth() - 40;
        int roomH = getHeight() - reminderHeight - 16;
        double scale = Math.min(1, Math.min((double) roomW / size[0], (double) roomH / size[1]));
        Graphics2D fitted = (Graphics2D) g.create();
        fitted.translate((getWidth() - size[0] * scale) / 2,
                8 + Math.max(0, (roomH - size[1] * scale) / 2));
        fitted.scale(scale, scale);
        paintRun(fitted, run, 0, 0);
        fitted.dispose();
    }
}
