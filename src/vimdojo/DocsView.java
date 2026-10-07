package vimdojo;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The reference, shown in a panel over whatever screen is open (see {@link #overlay()}) and read
 * like a manual page: every key on
 * the left, the selected one's entry on the right with its example playing in a small Vim, and
 * the pager's keys for getting about: / to search, n and N between matches, q to leave.
 */
final class DocsView extends JComponent {
    private static final int ROW = 30;
    private static final int HEADING = 40;
    private static final int TOP = 46;
    private static final int BOTTOM = 12;
    private static final int PAUSE_MS = 1200;
    /** The see-through dark layer put over everything behind the docs. */
    static final Color WASH = new Color(0, 0, 0, 125);

    private final App app;
    private final List<String> typed = new ArrayList<>();
    private int selected;
    private int scroll;
    // The right-hand side scrolls on its own when an entry is too long for the window.
    private int detailScroll;
    private int detailHeight;
    // The last search carried out, and the one being typed (null when none is).
    private String query = "";
    private String draft;
    private Vim vim;
    private int nextKey;
    private long nextAt;

    DocsView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> select(selected + 1);
                    case KeyEvent.VK_UP -> select(selected - 1);
                    case KeyEvent.VK_ESCAPE -> app.closeDocs();
                    default -> {
                    }
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                switch (e.getKeyChar()) {
                    case 'j' -> select(selected + 1);
                    case 'k' -> select(selected - 1);
                    case ' ', 'f' -> select(selected + page());
                    case 'b' -> select(selected - page());
                    case 'd' -> scrollDetail(1);
                    case 'u' -> scrollDetail(-1);
                    case 'n' -> nextMatch(1);
                    case 'N' -> nextMatch(-1);
                    case 'q' -> app.closeDocs();
                    default -> {
                    }
                }
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int index = entryAt(e.getX(), e.getY());
                if (index >= 0) {
                    select(index);
                }
            }
        };
        addMouseListener(mouse);
        addMouseWheelListener(e -> {
            int amount = (int) Math.round(e.getPreciseWheelRotation() * ROW);
            if (e.getX() > detailLeft() - 18) {
                detailScroll = Math.max(0, detailScroll + amount);
            } else {
                scroll = clamp(scroll + amount);
            }
            repaint();
        });
        new Timer(50, e -> {
            if (isShowing() && vim != null && now() >= nextAt) {
                step();
                repaint();
            }
        }).start();
    }

    /**
     * This view inside its overlay: a dark wash over the screen underneath, which stays visible
     * around the edges, and a solid panel on top holding the docs. Clicking the wash closes it.
     */
    JComponent overlay() {
        JPanel overlay = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.prep(g0);
                Theme t = Theme.current();
                g.setColor(WASH);
                g.fillRect(0, 0, getWidth(), getHeight());
                Insets in = getInsets();
                int w = getWidth() - in.left - in.right;
                int h = getHeight() - in.top - in.bottom;
                g.setColor(t.bg());
                g.fillRoundRect(in.left, in.top, w, h, 22, 22);
                g.setColor(t.sub());
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(in.left, in.top, w, h, 22, 22);
            }
        };
        overlay.setOpaque(false);
        // Wide margins, so enough of the screen underneath shows to make clear this sits on top.
        overlay.setBorder(BorderFactory.createEmptyBorder(26, 84, 30, 84));
        overlay.add(this, BorderLayout.CENTER);
        overlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                app.closeDocs();
            }
        });
        overlay.setVisible(false);
        return overlay;
    }

    private static long now() {
        return System.nanoTime() / 1_000_000;
    }

    void open() {
        select(selected);
    }

    int selected() {
        return selected;
    }

    /** d and u: half a window down or up through the selected entry's text. */
    private void scrollDetail(int direction) {
        detailScroll = Math.max(0, detailScroll + direction * (getHeight() - TOP - BOTTOM) / 2);
        repaint();
    }

    private int detailLeft() {
        int width = Math.min(getWidth() - 40, 1000);
        return Math.max(20, (getWidth() - width) / 2) + listWidth() + 36;
    }

    private int page() {
        return Math.max(1, (getHeight() - TOP - BOTTOM) / ROW - 2);
    }

    /** What the status bar shows: the current search and which match this is, or nothing. */
    String status() {
        if (query.isEmpty()) {
            return "";
        }
        List<Integer> found = matches(query);
        return "/" + query + " " + (found.indexOf(selected) + 1) + " of " + found.size();
    }

    // ---- search ----

    private static String searchable(Docs.Entry entry) {
        return (entry.group() + " " + entry.key() + " " + entry.does() + " " + entry.explain()
                + " " + String.join(" ", entry.more())).replace("`", "").toLowerCase();
    }

    /** Entries containing the text anywhere: key, description, notes or section name. */
    private static List<Integer> matches(String text) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < Docs.ALL.size() && !text.isEmpty(); i++) {
            if (searchable(Docs.ALL.get(i)).contains(text.toLowerCase())) {
                found.add(i);
            }
        }
        return found;
    }

    /** Lights up matches for a search that is still being typed; null when typing stops. */
    void preview(String text) {
        draft = text;
        repaint();
    }

    /**
     * Carries out a search, moving to the first match at or after the current entry and
     * wrapping around. An empty search repeats the last one. Returns false if nothing matches.
     */
    boolean search(String text) {
        draft = null;
        List<Integer> found = matches(text.isEmpty() ? query : text);
        if (found.isEmpty()) {
            repaint();
            return text.isEmpty() && query.isEmpty();
        }
        if (!text.isEmpty()) {
            query = text;
        }
        select(found.stream().filter(i -> i >= selected).findFirst().orElse(found.get(0)));
        return true;
    }

    /** n and N: to the next or previous match, wrapping around. */
    private void nextMatch(int direction) {
        List<Integer> found = matches(query);
        if (found.isEmpty()) {
            return;
        }
        int target = direction > 0
                ? found.stream().filter(i -> i > selected).findFirst().orElse(found.get(0))
                : found.stream().filter(i -> i < selected).reduce((a, b) -> b)
                        .orElse(found.get(found.size() - 1));
        select(target);
    }

    void jump(boolean top) {
        select(top ? 0 : Docs.ALL.size() - 1);
    }

    void select(int index) {
        selected = Math.max(0, Math.min(index, Docs.ALL.size() - 1));
        detailScroll = 0;
        // Scroll just enough to keep the selection, and its heading if it has one, in view.
        int rowTop = offsetOf(selected);
        int visible = Math.max(ROW, getHeight() - TOP - BOTTOM);
        boolean first = selected == 0
                || !Docs.ALL.get(selected - 1).group().equals(Docs.ALL.get(selected).group());
        if (rowTop - (first ? HEADING : 0) < scroll) {
            scroll = rowTop - (first ? HEADING : 0);
        } else if (rowTop + ROW > scroll + visible) {
            scroll = rowTop + ROW - visible;
        }
        scroll = clamp(scroll);
        restart();
        repaint();
    }

    /** Distance from the top of the list to an entry's row, counting the group headings. */
    private int offsetOf(int index) {
        int y = 0;
        for (int i = 0; i <= index; i++) {
            if (i == 0 || !Docs.ALL.get(i - 1).group().equals(Docs.ALL.get(i).group())) {
                y += HEADING;
            }
            if (i < index) {
                y += ROW;
            }
        }
        return y;
    }

    private int clamp(int position) {
        int total = offsetOf(Docs.ALL.size() - 1) + ROW;
        return Math.max(0, Math.min(position, total - Math.max(ROW, getHeight() - TOP - BOTTOM)));
    }

    private int listWidth() {
        return Math.min(470, getWidth() * 46 / 100);
    }

    private int entryAt(int x, int y) {
        int left = Math.max(20, (getWidth() - Math.min(getWidth() - 40, 1000)) / 2);
        if (x < left || x > left + listWidth()) {
            return -1;
        }
        for (int i = 0; i < Docs.ALL.size(); i++) {
            int rowTop = TOP + offsetOf(i) - scroll;
            if (y >= rowTop && y < rowTop + ROW) {
                return i;
            }
        }
        return -1;
    }

    // ---- the example ----

    private void restart() {
        Docs.Entry entry = Docs.ALL.get(selected);
        vim = entry.hasDemo() ? entry.vim() : null;
        nextKey = 0;
        typed.clear();
        nextAt = now() + PAUSE_MS;
    }

    /** Plays the next key of the example, or starts it again once it has finished. */
    void step() {
        if (vim == null) {
            return;
        }
        String keys = Keys.parse(Docs.ALL.get(selected).keys());
        if (nextKey >= keys.length()) {
            restart();
            return;
        }
        char key = keys.charAt(nextKey++);
        typed.add(switch (key) {
            case Vim.ESC -> "esc";
            case Vim.ENTER -> "enter";
            case Vim.BACKSPACE -> "bksp";
            case Vim.CTRL_R -> "ctrl-r";
            case ' ' -> "space";
            default -> String.valueOf(key);
        });
        vim.key(key);
        boolean typing = vim.mode() == Vim.Mode.INSERT || vim.mode() == Vim.Mode.SEARCH;
        nextAt = now() + (nextKey == keys.length() ? PAUSE_MS + 900 : typing ? 230 : 700);
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        int width = Math.min(getWidth() - 40, 1000);
        int left = Math.max(20, (getWidth() - width) / 2);
        int listWidth = listWidth();

        // The line across the top of a manual page: its name either side, the section between.
        g.setFont(Theme.mono(12.5f));
        g.setColor(t.sub());
        String name = "VIMDOJO(1)";
        g.drawString(name, left, 22);
        g.drawString(name, left + width - g.getFontMetrics().stringWidth(name), 22);
        String section = Docs.ALL.get(selected).group();
        g.drawString(section, left + (width - g.getFontMetrics().stringWidth(section)) / 2, 22);

        paintList(g, left, listWidth);
        paintDetail(g, left + listWidth + 36, width - listWidth - 36);
    }

    private void paintList(Graphics2D g, int left, int listWidth) {
        Theme t = Theme.current();
        Graphics2D clipped = (Graphics2D) g.create();
        clipped.clipRect(0, TOP - 6, getWidth(), getHeight() - TOP - BOTTOM + 12);
        String shown = draft != null ? draft : query;
        List<Integer> found = matches(shown);
        String group = "";
        int y = TOP - scroll;
        for (int i = 0; i < Docs.ALL.size(); i++) {
            Docs.Entry entry = Docs.ALL.get(i);
            if (!entry.group().equals(group)) {
                group = entry.group();
                Paint.label(clipped, group, left, y + 26);
                y += HEADING;
            }
            if (i == selected) {
                Paint.panel(clipped, left, y, listWidth, ROW - 2);
                clipped.setColor(t.accent());
                clipped.fillRoundRect(left, y + 6, 4, ROW - 14, 4, 4);
            }
            int x = left + 14;
            if (entry.isTerm()) {
                clipped.setFont(Theme.bold(13.5f));
                clipped.setColor(t.accent());
                clipped.drawString(entry.key(), x, y + 19);
                x += clipped.getFontMetrics().stringWidth(entry.key());
            } else {
                for (String key : entry.key().split("  ")) {
                    x += Paint.keycap(clipped, key, x, y + 19, 12f) + 5;
                }
            }
            clipped.setFont(i == selected ? Theme.bold(13.5f) : Theme.ui(13.5f));
            clipped.setColor(t.text());
            // Descriptions start in one column unless an unusually wide key pushes them along.
            String does = entry.does();
            int textX = Math.max(x + 8, left + 150);
            FontMetrics fm = clipped.getFontMetrics();
            while (does.length() > 4 && textX + fm.stringWidth(does) > left + listWidth - 8) {
                does = does.substring(0, does.length() - 2).stripTrailing() + "…";
            }
            if (found.contains(i)) {
                // Mark the matching words; if the match is in the notes, mark the row instead.
                int at = does.toLowerCase().indexOf(shown.toLowerCase());
                clipped.setColor(t.wash(120));
                if (at >= 0) {
                    clipped.fillRoundRect(textX + fm.stringWidth(does.substring(0, at)) - 2, y + 5,
                            fm.stringWidth(does.substring(at, at + shown.length())) + 4, 19, 6, 6);
                } else {
                    clipped.fillOval(left + listWidth - 16, y + 10, 8, 8);
                }
                clipped.setColor(t.text());
            }
            clipped.drawString(does, textX, y + 19);
            y += ROW;
        }
        clipped.dispose();
    }

    /**
     * The selected entry: its name and description, the example playing, then the explanation
     * and notes. Scrolls by itself when that is taller than the window.
     */
    private void paintDetail(Graphics2D g0, int x, int width) {
        Theme t = Theme.current();
        Docs.Entry entry = Docs.ALL.get(selected);
        int top = TOP + 26;
        int bottom = getHeight() - BOTTOM;
        Graphics2D g = (Graphics2D) g0.create();
        g.clipRect(x - 6, TOP - 6, width + 12, bottom - TOP + 6);
        int y = top - detailScroll;

        Paint.label(g, entry.isTerm() ? "term" : "name", x, y);
        y += 46;
        if (entry.isTerm()) {
            g.setFont(Theme.bold(24f));
            g.setColor(t.text());
            g.drawString(entry.key(), x, y);
        } else {
            int keyX = x;
            for (String key : entry.key().split("  ")) {
                keyX += Paint.keycap(g, key, keyX, y, 24f) + 10;
            }
        }
        y += 40;
        // The full description, wrapped, since the list may have shortened it.
        for (String line : Paint.wrap(g, entry.does(), width, 17f)) {
            Paint.prose(g, line, x, y, 17f, t.text());
            y += 26;
        }
        y += 18;

        if (vim != null) {
            y = paintExample(g, x, y, width) + 18;
        }
        if (!entry.explain().isEmpty()) {
            Paint.label(g, "in detail", x, y);
            y += 30;
            for (String paragraph : entry.explain().split("\n")) {
                for (String line : Paint.wrap(g, paragraph, width, 15f)) {
                    Paint.prose(g, line, x, y, 15f, t.text());
                    y += 23;
                }
                y += 11;
            }
            y += 14;
        }
        if (!entry.more().isEmpty()) {
            Paint.label(g, "notes", x, y);
            y += 28;
            for (String extra : entry.more()) {
                for (String line : Paint.wrap(g, extra, width, 14f)) {
                    Paint.prose(g, line, x, y, 14f, t.text());
                    y += 22;
                }
                y += 6;
            }
        }
        g.dispose();

        detailHeight = y + detailScroll - top;
        int room = bottom - top;
        int most = Math.max(0, detailHeight - room + 10);
        if (detailScroll > most) {
            detailScroll = most;
            repaint();
        }
        if (detailScroll < most) {
            // More below: fade the text out and say how to reach the rest.
            Color bg = t.bg();
            g0.setPaint(new GradientPaint(0, bottom - 54, new Color(bg.getRed(), bg.getGreen(),
                    bg.getBlue(), 0), 0, bottom - 22, bg));
            g0.fillRect(x - 6, bottom - 54, width + 12, 54);
            Paint.prose(g0, "more below: scroll or `d`", x, bottom - 6, 12.5f, t.sub());
        }
    }

    /** The example's buffer and the keys pressed so far. Returns the y below them. */
    private int paintExample(Graphics2D g, int x, int y, int width) {
        Theme t = Theme.current();
        Docs.Entry entry = Docs.ALL.get(selected);
        int labelWidth = Paint.label(g, "example", x, y);
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
        y += 16;

        // The buffer, shrunk if a long line wouldn't fit the panel.
        float font = 20f;
        int longest = 1;
        int startLines = entry.vim().lines().size();
        for (String l : vim.lines()) {
            longest = Math.max(longest, l.length() + 1);
        }
        for (String l : entry.vim().lines()) {
            longest = Math.max(longest, l.length() + 1);
        }
        int charW = g.getFontMetrics(Theme.mono(font)).charWidth('m');
        double scale = Math.min(1, (width - 12.0) / (charW * (longest + 3) + 44));
        int lineHeight = 31;
        // Room for a line the example adds, so the panel doesn't change size while it plays.
        int rows = Math.max(vim.lines().size(), startLines + 1);
        int panelHeight = (int) ((rows * lineHeight + 36) * scale);
        Paint.panel(g, x, y, width, panelHeight);
        Graphics2D small = (Graphics2D) g.create();
        small.translate(x, y);
        small.scale(scale, scale);
        Paint.buffer(small, vim, 22 + charW * 3, 18, font, lineHeight, -1, -1);
        small.dispose();
        y += panelHeight + 34;

        // The keys pressed so far, the newest one outlined. Room is kept for one row of them.
        int keyX = x;
        for (int i = 0; i < typed.size(); i++) {
            String name = typed.get(i);
            int keyWidth = Paint.keycapWidth(g, name, 13f);
            if (keyX + keyWidth > x + width) {
                keyX = x;
                y += 32;
            }
            Paint.keycap(g, name, keyX, y, 13f);
            if (i == typed.size() - 1) {
                g.setColor(t.accent());
                g.setStroke(new BasicStroke(1.6f));
                g.drawRoundRect(keyX - 2, y - 17, keyWidth + 4, 25, 8, 8);
            }
            keyX += keyWidth + 6;
        }
        return y + 16;
    }
}
