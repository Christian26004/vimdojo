package vimdojo;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The lesson itself. First an introduction to the new keys, then one task at a time: a prompt,
 * the buffer with its cursor, and for editing tasks the text to aim for.
 */
final class ChallengeView extends JComponent {
    private static final float FONT_SIZE = 24f;
    private static final int LINE_HEIGHT = 38;
    private static final int PAUSE_AFTER_TASK_MS = 450;

    private final App app;
    private boolean intro = true;

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
                            intro = false;
                            app.run().shown(now());
                            repaint();
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
                if (c >= 32 && c != 127 && !e.isControlDown() && !e.isMetaDown()) {
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
    }

    private static long now() {
        return System.nanoTime() / 1_000_000;
    }

    /** Show the introduction for the run the app has just created. */
    void begin() {
        intro = true;
        repaint();
    }

    private void feed(char c) {
        Run run = app.run();
        if (intro) {
            return;
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
                repaint();
            });
            next.setRepeats(false);
            next.start();
        }
        repaint();
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
        List<Lesson.Key> keys = lesson.keys();
        int rowHeight = 40;
        int top = Math.max(30, (getHeight() - (130 + keys.size() * rowHeight)) / 2 - 20);

        g.setFont(Theme.font(14f));
        g.setColor(t.sub());
        Paint.centered(g, "lesson " + (app.lessonIndex() + 1) + " of " + Lessons.ALL.size(),
                getWidth(), top);
        g.setFont(Theme.font(32f));
        g.setColor(t.main());
        Paint.centered(g, lesson.title(), getWidth(), top + 48);

        g.setFont(Theme.font(22f));
        FontMetrics keyMetrics = g.getFontMetrics();
        int keyWidth = keys.stream().mapToInt(k -> keyMetrics.stringWidth(k.key())).max().orElse(0);
        g.setFont(Theme.font(17f));
        int textWidth = keys.stream().mapToInt(k -> g.getFontMetrics().stringWidth(k.does())).max()
                .orElse(0);
        int left = (getWidth() - (keyWidth + 36 + textWidth)) / 2;
        int y = top + 120;
        for (Lesson.Key key : keys) {
            g.setFont(Theme.font(22f));
            g.setColor(t.main());
            g.drawString(key.key(), left, y);
            g.setFont(Theme.font(17f));
            g.setColor(t.text());
            g.drawString(key.does(), left + keyWidth + 36, y - 1);
            y += rowHeight;
        }

        g.setFont(Theme.font(13f));
        g.setColor(t.sub());
        Paint.centered(g, "enter  -  begin", getWidth(), getHeight() - 28);
    }

    private void paintTask(Graphics2D g) {
        Theme t = Theme.current();
        Run run = app.run();
        Task task = run.task();
        Vim vim = run.vim();
        List<String> lines = vim.lines();
        String[] goal = task.isMotion() ? new String[0] : task.goal().split("\n", -1);
        // Reserve room for the taller of the start and goal texts so the layout doesn't jump
        // around while lines are added and removed.
        int rows = Math.max(lines.size(), Math.max(task.start().split("\n", -1).length,
                goal.length));
        int goalHeight = goal.length == 0 ? 0 : 44 + goal.length * LINE_HEIGHT;
        int height = 86 + rows * LINE_HEIGHT + 34 + goalHeight;
        int top = Math.max(16, (getHeight() - 60 - height) / 2);

        g.setFont(Theme.font(FONT_SIZE));
        FontMetrics fm = g.getFontMetrics();
        int charW = fm.charWidth('m');
        int gutter = charW * 3;
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, line.length());
        }
        for (String line : goal) {
            widest = Math.max(widest, line.length());
        }
        for (String line : task.start().split("\n", -1)) {
            widest = Math.max(widest, line.length());
        }
        int width = Math.max(gutter + widest * charW, 560);
        int left = Math.max(30, (getWidth() - width) / 2);

        // Progress and prompt.
        g.setColor(t.main());
        g.drawString((run.index() + 1) + "/" + run.tasks().size(), left, top + 24);
        g.setFont(Theme.font(17f));
        Paint.rich(g, task.prompt(), left, top + 62, t.text(), t.main());

        // The buffer.
        boolean done = run.waiting();
        int textLeft = left + gutter;
        int y = top + 86;
        for (int r = 0; r < lines.size(); r++) {
            String line = lines.get(r);
            int baseline = y + r * LINE_HEIGHT + fm.getAscent();
            g.setFont(Theme.font(15f));
            g.setColor(t.sub());
            String number = Integer.toString(r + 1);
            g.drawString(number, textLeft - charW - g.getFontMetrics().stringWidth(number),
                    baseline - 2);
            g.setFont(Theme.font(FONT_SIZE));
            // One cell past the end, because visual mode can put the cursor there.
            for (int c = 0; c <= line.length(); c++) {
                int x = textLeft + c * charW;
                int cellTop = y + r * LINE_HEIGHT;
                boolean cursor = r == vim.row() && c == vim.col()
                        && vim.mode() != Vim.Mode.INSERT;
                boolean target = task.isMotion() && r == task.goalRow() && c == task.goalCol();
                if (cursor) {
                    g.setColor(t.main());
                    g.fillRoundRect(x, cellTop + 1, charW, fm.getHeight() - 2, 5, 5);
                    g.setColor(t.bg());
                } else if (vim.selected(r, c)) {
                    g.setColor(t.sub());
                    g.fillRect(x, cellTop + 1, charW, fm.getHeight() - 2);
                    g.setColor(t.text());
                } else if (target) {
                    g.setColor(t.subAlt());
                    g.fillRoundRect(x - 1, cellTop, charW + 2, fm.getHeight(), 5, 5);
                    g.setColor(t.main());
                    g.fillRect(x, cellTop + fm.getHeight() - 3, charW, 3);
                } else {
                    g.setColor(done ? t.main() : t.text());
                }
                if (c < line.length()) {
                    g.drawString(String.valueOf(line.charAt(c)), x, baseline);
                }
            }
            if (vim.mode() == Vim.Mode.INSERT && r == vim.row()) {
                g.setColor(t.main());
                g.fillRoundRect(textLeft + vim.col() * charW - 1, y + r * LINE_HEIGHT + 1, 3,
                        fm.getHeight() - 2, 3, 3);
            }
        }

        // Mode and half-typed command, the way Vim shows them under the text.
        int statusY = y + rows * LINE_HEIGHT + 22;
        g.setFont(Theme.font(15f));
        g.setColor(t.sub());
        switch (vim.mode()) {
            case INSERT -> g.drawString("-- INSERT --", textLeft, statusY);
            case VISUAL -> g.drawString("-- VISUAL --", textLeft, statusY);
            case VISUAL_LINE -> g.drawString("-- VISUAL LINE --", textLeft, statusY);
            case SEARCH -> {
                g.setColor(t.text());
                g.drawString("/" + vim.searchText(), textLeft, statusY);
            }
            default -> {
            }
        }
        if (!vim.pending().isEmpty()) {
            g.setColor(t.main());
            g.drawString(vim.pending(), textLeft + (vim.mode() == Vim.Mode.NORMAL ? 0 : 22 * 9),
                    statusY);
        }

        if (goal.length > 0) {
            int goalY = statusY + 34;
            g.setFont(Theme.font(13f));
            g.setColor(t.sub());
            g.drawString("goal", textLeft, goalY);
            g.setFont(Theme.font(FONT_SIZE));
            for (int r = 0; r < goal.length; r++) {
                g.drawString(goal[r], textLeft, goalY + 10 + r * LINE_HEIGHT + fm.getAscent());
            }
        }

        // Reminder of this lesson's keys.
        StringBuilder reminder = new StringBuilder();
        for (Lesson.Key key : run.lesson().keys()) {
            reminder.append(reminder.isEmpty() ? "" : "     ").append('`').append(key.key())
                    .append("` ").append(key.does());
        }
        g.setFont(Theme.font(13f));
        if (Paint.richWidth(g, reminder.toString()) <= getWidth() - 40) {
            Paint.rich(g, reminder.toString(),
                    (getWidth() - Paint.richWidth(g, reminder.toString())) / 2, getHeight() - 52,
                    t.sub(), t.main());
        }
        g.setColor(t.sub());
        Paint.centered(g, "tab  -  restart lesson", getWidth(), getHeight() - 28);
    }
}
