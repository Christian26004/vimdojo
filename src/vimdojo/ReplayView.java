package vimdojo;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A small panel over the results screen that replays one task the way par did it: the task's
 * own text, with the par keys pressed one at a time, and underneath the keys you pressed.
 */
final class ReplayView extends JComponent {
    private static final int WIDTH = 560;
    private static final int PAD = 24;
    private static final int LINE_HEIGHT = 31;
    private static final int KEY_ROW = 30;
    private static final float KEY_SIZE = 12.5f;
    private static final int PAUSE_MS = 1100;
    private static final int MAX_BUFFER_HEIGHT = 230;
    /** For measuring text before there is anything to paint on. */
    private static final Graphics2D MEASURE =
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();

    private final App app;
    private final List<String> typed = new ArrayList<>();
    private Run run;
    private int task;
    private Vim vim;
    private int rows;
    private int nextKey;
    private long nextAt;

    ReplayView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> app.closeReplay();
                    case KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT -> show(run, task + 1);
                    case KeyEvent.VK_UP, KeyEvent.VK_LEFT -> show(run, task - 1);
                    default -> {
                    }
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                switch (e.getKeyChar()) {
                    case 'q' -> app.closeReplay();
                    case 'j', 'l' -> show(run, task + 1);
                    case 'k', 'h' -> show(run, task - 1);
                    default -> {
                    }
                }
            }
        });
        // Swallow clicks on the panel itself; only clicks outside it close the replay.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });
        new Timer(50, e -> {
            if (isShowing() && vim != null && now() >= nextAt) {
                step();
                repaint();
            }
        }).start();
    }

    private static long now() {
        return System.nanoTime() / 1_000_000;
    }

    /** This view centred over a dark wash; clicking the wash closes it. */
    JComponent overlay() {
        JPanel overlay = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(DocsView.WASH);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        overlay.setOpaque(false);
        overlay.add(this);
        overlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                app.closeReplay();
            }
        });
        overlay.setVisible(false);
        return overlay;
    }

    int task() {
        return task;
    }

    /** Starts replaying a task of a finished run; out-of-range numbers are pulled back in. */
    void show(Run run, int index) {
        this.run = run;
        task = Math.max(0, Math.min(index, run.tasks().size() - 1));
        restart();
        // Tall enough for the text at its longest, so the panel doesn't resize while it plays.
        Vim end = prepared(task);
        rows = end.lines().size();
        for (char k : Keys.parse(run.tasks().get(task).solution()).toCharArray()) {
            end.key(k);
            rows = Math.max(rows, end.lines().size());
        }
        setPreferredSize(new Dimension(WIDTH, height()));
        revalidate();
        app.refresh();
    }

    /**
     * Vim as it stood when the task began. Tasks that carried on in the same buffer, as movement
     * drills and searches do, inherit what came before, such as the last search for n to repeat.
     */
    private Vim prepared(int index) {
        Task t = run.tasks().get(index);
        if (index > 0) {
            Task before = run.tasks().get(index - 1);
            if (before.isMotion() && before.start().equals(t.start())
                    && before.goalRow() == t.row() && before.goalCol() == t.col()) {
                Vim carried = prepared(index - 1);
                for (char k : Keys.parse(before.solution()).toCharArray()) {
                    carried.key(k);
                }
                return carried;
            }
        }
        return new Vim(t.start(), t.row(), t.col());
    }

    private void restart() {
        vim = prepared(task);
        nextKey = 0;
        typed.clear();
        nextAt = now() + PAUSE_MS;
    }

    /** Plays the next par key, or starts over once the task is done. */
    void step() {
        if (vim == null) {
            return;
        }
        String keys = Keys.parse(run.tasks().get(task).solution());
        if (nextKey >= keys.length()) {
            restart();
            return;
        }
        char key = keys.charAt(nextKey++);
        typed.add(perKey(Keys.notation(String.valueOf(key))).get(0));
        vim.key(key);
        boolean typing = vim.mode() == Vim.Mode.INSERT || vim.mode() == Vim.Mode.SEARCH;
        nextAt = now() + (nextKey == keys.length() ? PAUSE_MS + 900 : typing ? 230 : 700);
    }

    // ---- layout ----

    private double bufferScale() {
        int longest = 1;
        for (String line : run.tasks().get(task).start().split("\n", -1)) {
            longest = Math.max(longest, line.length() + 1);
        }
        String goal = run.tasks().get(task).goal();
        for (String line : goal == null ? new String[0] : goal.split("\n", -1)) {
            longest = Math.max(longest, line.length() + 1);
        }
        int charW = MEASURE.getFontMetrics(Theme.mono(20f)).charWidth('m');
        // Shrunk to fit the panel's width, and again if a long text would make the panel tall.
        return Math.min(1, Math.min((WIDTH - PAD * 2 - 12.0) / (charW * (longest + 3) + 44),
                (double) MAX_BUFFER_HEIGHT / (rows * LINE_HEIGHT + 36)));
    }

    private int bufferHeight() {
        return (int) ((rows * LINE_HEIGHT + 36) * bufferScale());
    }

    /** One keycap name per key press, the way the par keys are shown as they play. */
    private static List<String> perKey(String notation) {
        List<String> names = new ArrayList<>();
        for (char key : Keys.parse(notation).toCharArray()) {
            names.add(Paint.chunks(Keys.notation(String.valueOf(key))).get(0));
        }
        return names;
    }

    /** How many rows a list of keycaps needs at the panel's width. */
    private static int keyRows(List<String> chunks) {
        int rows = 1;
        int x = 0;
        for (String chunk : chunks) {
            int width = Paint.keycapWidth(MEASURE, chunk, KEY_SIZE) + 5;
            if (x > 0 && x + width > WIDTH - PAD * 2) {
                rows++;
                x = 0;
            }
            x += width;
        }
        return rows;
    }

    private int height() {
        Task t = run.tasks().get(task);
        return PAD + 16 + 40 + bufferHeight() + 40 + keyRows(perKey(t.solution())) * KEY_ROW + 30
                + keyRows(Paint.chunks(Keys.notation(run.typed(task)))) * KEY_ROW + PAD - 8;
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics g0) {
        if (run == null) {
            return;
        }
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        Task current = run.tasks().get(task);
        g.setColor(t.bg());
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
        g.setColor(t.sub());
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);

        int x = PAD;
        int width = getWidth() - PAD * 2;
        int y = PAD + 12;
        int labelWidth = Paint.label(g, "task " + (task + 1) + " of " + run.tasks().size(), x, y);
        String mode = switch (vim.mode()) {
            case NORMAL -> "normal";
            case INSERT -> "insert";
            case VISUAL -> "visual";
            case VISUAL_LINE -> "v-line";
            case SEARCH -> "search /" + vim.searchText();
        };
        g.setFont(Theme.caps(10.5f));
        g.setColor(vim.mode() == Vim.Mode.NORMAL ? t.sub() : t.accent());
        g.drawString(mode.toUpperCase(), x + labelWidth + 16, y);
        y += 30;
        Paint.prose(g, current.prompt(), x, y, 15f, t.text());
        y += 18;

        // The task's text, with the par keys acting on it.
        double scale = bufferScale();
        int charW = g.getFontMetrics(Theme.mono(20f)).charWidth('m');
        Paint.panel(g, x, y, width, bufferHeight());
        Graphics2D small = (Graphics2D) g.create();
        small.translate(x, y);
        small.scale(scale, scale);
        Paint.buffer(small, vim, 22 + charW * 3, 18, 20f, LINE_HEIGHT,
                current.isMotion() ? current.goalRow() : -1, current.goalCol());
        small.dispose();
        y += bufferHeight() + 30;

        Paint.label(g, "par, " + current.par() + (current.par() == 1 ? " key" : " keys"), x, y);
        y += 26;
        y = keys(g, typed, x, y, width, true);

        int used = run.keys(task);
        y += 30;
        g.setFont(Theme.caps(10.5f));
        g.setColor(used > current.par() ? t.accent() : used < current.par() ? t.good() : t.sub());
        g.drawString(("you pressed, " + used + (used == 1 ? " key" : " keys")).toUpperCase(), x, y);
        y += 26;
        keys(g, Paint.chunks(Keys.notation(run.typed(task))), x, y, width, false);
    }

    /** Draws keycaps, wrapping at the panel's width. Returns the baseline of the last row. */
    private int keys(Graphics2D g, List<String> chunks, int x, int y, int width, boolean live) {
        int keyX = x;
        for (int i = 0; i < chunks.size(); i++) {
            int keyWidth = Paint.keycapWidth(g, chunks.get(i), KEY_SIZE);
            if (keyX > x && keyX + keyWidth > x + width) {
                keyX = x;
                y += KEY_ROW;
            }
            Paint.keycap(g, chunks.get(i), keyX, y, KEY_SIZE);
            if (live && i == chunks.size() - 1) {
                // The key just pressed.
                g.setColor(Theme.current().accent());
                g.setStroke(new BasicStroke(1.6f));
                FontMetrics fm = g.getFontMetrics(Theme.mono(KEY_SIZE));
                g.drawRoundRect(keyX - 2, y - fm.getAscent() - 4, keyWidth + 4,
                        fm.getAscent() + fm.getDescent() + 9, 8, 8);
            }
            keyX += keyWidth + 5;
        }
        return y;
    }
}
