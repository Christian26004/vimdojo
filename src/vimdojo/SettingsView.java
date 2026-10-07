package vimdojo;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** Options, plus a reference for getting around the app from the keyboard. */
final class SettingsView extends JComponent {
    private static final int ROW = 42;
    private static final int ROWS = 3;
    private static final String[][] REFERENCE = {
        {":docs", "every key, with examples"},
        {":lessons", "the lesson list"},
        {":stats", "your statistics"},
        {":settings", "this screen"},
        {":lesson  :ready", "back to the current lesson"},
        {":next  :prev", "the lesson after or before"},
        {":7", "lesson 7 - any number"},
        {":restart", "start the lesson again"},
        {":colo paper", "switch theme"},
        {":dvorak  :qwerty", "switch keyboard layout"},
        {":q", "quit"},
        {"gt  gT", "next or previous screen"},
        {"j  k", "move down or up in a list"},
        {"gg  G", "top or bottom of a list"},
        {"h  l", "other lessons, from the ready screen"},
        {"enter", "open or confirm"},
    };

    /** Somewhere on screen that can be clicked, and what clicking it does. */
    private record Hotspot(Rectangle area, Runnable action) {
    }

    private final App app;
    // Rebuilt on every paint, so the clickable areas always match what is drawn.
    private final List<Hotspot> hotspots = new ArrayList<>();
    private int selected;
    private boolean confirmErase;
    private int scroll;
    private int contentHeight;

    SettingsView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> select(selected + 1);
                    case KeyEvent.VK_UP -> select(selected - 1);
                    case KeyEvent.VK_RIGHT -> change(1);
                    case KeyEvent.VK_LEFT -> change(-1);
                    case KeyEvent.VK_ENTER -> activate();
                    case KeyEvent.VK_ESCAPE -> select(selected);
                    default -> {
                    }
                }
            }

            // Letters are read as typed characters so they follow the keyboard layout setting.
            @Override
            public void keyTyped(KeyEvent e) {
                switch (e.getKeyChar()) {
                    case 'j' -> select(selected + 1);
                    case 'k' -> select(selected - 1);
                    case 'l' -> change(1);
                    case 'h' -> change(-1);
                    default -> {
                    }
                }
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Hotspot hit = hotspotAt(e);
                if (hit != null) {
                    hit.action.run();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(hotspotAt(e) != null ? Cursor.HAND_CURSOR
                        : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        // Only needed when the window is too short to show everything.
        addMouseWheelListener(e -> {
            scroll = Math.max(0, Math.min(contentHeight - getHeight(),
                    scroll + (int) Math.round(e.getPreciseWheelRotation() * 30)));
            repaint();
        });
    }

    void open() {
        scroll = 0;
        select(0);
    }

    private Hotspot hotspotAt(MouseEvent e) {
        // Later hotspots sit on top of earlier ones: a choice wins over the row behind it.
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            if (hotspots.get(i).area.contains(e.getPoint())) {
                return hotspots.get(i);
            }
        }
        return null;
    }

    /** Moving the selection also cancels a pending erase. */
    void select(int row) {
        selected = Math.max(0, Math.min(row, ROWS - 1));
        confirmErase = false;
        repaint();
    }

    void jump(boolean top) {
        select(top ? 0 : ROWS - 1);
    }

    /** Steps the selected option left or right. */
    private void change(int step) {
        if (selected == 0) {
            int count = Theme.ALL.length;
            int index = java.util.Arrays.asList(Theme.ALL).indexOf(Theme.current());
            app.setTheme(Theme.ALL[(index + step + count) % count].name());
        } else if (selected == 1) {
            app.setDvorak(!app.dvorak());
        }
    }

    private void activate() {
        if (selected < 2) {
            change(1);
        } else if (confirmErase) {
            app.history().clear();
            confirmErase = false;
            app.refresh();
        } else if (!app.history().all().isEmpty()) {
            // Erasing can't be undone, so it takes a second press.
            confirmErase = true;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        int width = Math.min(getWidth() - 80, 920);
        int left = (getWidth() - width) / 2;
        int y = 12 - scroll;

        Paint.label(g, "settings", left, y + 10);
        y += 24;
        hotspots.clear();
        int runs = app.history().all().size();
        for (int row = 0; row < ROWS; row++) {
            int rowY = y + row * ROW;
            int clicked = row;
            // Clicking a row selects it; clicking the erase row also presses it.
            hotspots.add(new Hotspot(new Rectangle(left, rowY, width, ROW - 6), () -> {
                if (clicked == 2 && selected == 2) {
                    activate();
                } else {
                    select(clicked);
                    if (clicked == 2) {
                        activate();
                    }
                }
            }));
            if (row == selected) {
                Paint.panel(g, left, rowY, width, ROW - 6);
                g.setColor(t.accent());
                g.fillRoundRect(left, rowY + 7, 4, ROW - 20, 4, 4);
            }
            int baseline = rowY + 24;
            g.setFont(row == selected ? Theme.bold(15f) : Theme.ui(15f));
            g.setColor(t.text());
            g.drawString(new String[] {"Theme", "Keyboard layout", "Erase progress"}[row],
                    left + 20, baseline);
            int x = left + 220;
            if (row < 2) {
                String[] choices = row == 0
                        ? java.util.Arrays.stream(Theme.ALL).map(Theme::name).toArray(String[]::new)
                        : new String[] {"qwerty", "dvorak"};
                String chosen = row == 0 ? t.name() : app.dvorak() ? "dvorak" : "qwerty";
                for (String choice : choices) {
                    boolean current = choice.equals(chosen);
                    g.setFont(current ? Theme.bold(15f) : Theme.ui(15f));
                    g.setColor(current ? t.text() : t.sub());
                    g.drawString(choice, x, baseline);
                    int nameWidth = g.getFontMetrics().stringWidth(choice);
                    if (current) {
                        g.setColor(t.accent());
                        g.fillRect(x, baseline + 6, nameWidth, 3);
                    }
                    hotspots.add(new Hotspot(new Rectangle(x - 12, rowY, nameWidth + 24, ROW - 6),
                            () -> {
                                select(clicked);
                                if (clicked == 0) {
                                    app.setTheme(choice);
                                } else {
                                    app.setDvorak(choice.equals("dvorak"));
                                }
                            }));
                    x += nameWidth + 28;
                }
                if (row == 1) {
                    g.setFont(Theme.ui(13f));
                    g.setColor(t.sub());
                    g.drawString("assumes your system is set to QWERTY", x + 16, baseline);
                }
            } else {
                g.setFont(confirmErase ? Theme.bold(15f) : Theme.ui(15f));
                g.setColor(confirmErase ? t.accent() : t.sub());
                g.drawString(confirmErase ? "Press enter again to erase all " + runs + " runs."
                        : (runs == 0 ? "Nothing recorded yet" : runs + (runs == 1 ? " run" : " runs")
                        + " recorded") + ", kept in " + Settings.dataDir(), x, baseline);
            }
        }
        y += ROWS * ROW + 30;

        Paint.label(g, "getting around", left, y);
        y += 30;
        // Two columns if the longest entry fits in half the width, otherwise one.
        int widest = 0;
        for (String[] entry : REFERENCE) {
            widest = Math.max(widest, 166 + g.getFontMetrics(Theme.ui(14f)).stringWidth(entry[1]));
        }
        int half = widest * 2 + 30 <= width ? (REFERENCE.length + 1) / 2 : REFERENCE.length;
        for (int i = 0; i < REFERENCE.length; i++) {
            int x = left + (i < half ? 0 : width / 2);
            int baseline = y + (i % half) * 30;
            int at = x;
            for (String key : REFERENCE[i][0].split("  ")) {
                at += Paint.keycap(g, key, at, baseline, 12.5f) + 6;
            }
            g.setFont(Theme.ui(14f));
            g.setColor(t.text());
            g.drawString(REFERENCE[i][1], x + 166, baseline);
        }
        contentHeight = y + half * 30 + scroll;
    }
}
