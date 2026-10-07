package vimdojo;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.OptionalDouble;
import javax.swing.JComponent;

/** The list of lessons with your best result for each. Navigated the Vim way, with j and k. */
final class LessonsView extends JComponent {
    private static final int ROW = 34;
    private static final int TOP = 44;
    private static final int BOTTOM = 8;

    private final App app;
    private int selected;
    private int scroll;

    LessonsView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> select(selected + 1);
                    case KeyEvent.VK_UP -> select(selected - 1);
                    case KeyEvent.VK_ENTER -> app.startLesson(selected);
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
                    default -> {
                    }
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = (e.getY() - TOP + scroll) / ROW;
                if (e.getY() >= TOP && row >= 0 && row < Lessons.ALL.size()) {
                    app.startLesson(row);
                }
            }
        });
        addMouseWheelListener(e -> {
            scroll = clamp(scroll + (int) Math.round(e.getPreciseWheelRotation() * ROW));
            repaint();
        });
    }

    void open(int lesson) {
        scroll = 0;
        select(lesson);
    }

    void jump(boolean top) {
        select(top ? 0 : Lessons.ALL.size() - 1);
    }

    private void select(int index) {
        selected = Math.max(0, Math.min(index, Lessons.ALL.size() - 1));
        // Scroll just enough to keep the selected row on screen.
        int rowTop = selected * ROW;
        int visible = Math.max(ROW, getHeight() - TOP - BOTTOM);
        if (rowTop < scroll) {
            scroll = rowTop;
        } else if (rowTop + ROW > scroll + visible) {
            scroll = rowTop + ROW - visible;
        }
        scroll = clamp(scroll);
        repaint();
    }

    /** Keeps a scroll position within the list; zero when everything fits. */
    private int clamp(int position) {
        int overflow = Lessons.ALL.size() * ROW - Math.max(ROW, getHeight() - TOP - BOTTOM);
        return Math.max(0, Math.min(position, overflow));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        int width = Math.min(getWidth() - 80, 920);
        int left = (getWidth() - width) / 2;
        int bestX = left + width - 150;

        Paint.label(g, "lesson", left + 50, 26);
        Paint.label(g, "keys", left + width * 34 / 100, 26);
        Paint.label(g, "best", bestX, 26);

        g.clipRect(0, TOP - 4, getWidth(), getHeight() - TOP + 4);
        java.awt.Composite normal = g.getComposite();
        for (int i = 0; i < Lessons.ALL.size(); i++) {
            Lesson lesson = Lessons.ALL.get(i);
            int y = TOP + i * ROW - scroll;
            // Lessons not yet open are greyed out, all but their number and what opens them.
            boolean locked = !app.unlocked(i);
            g.setComposite(locked ? java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.35f) : normal);
            if (i == selected) {
                Paint.panel(g, left, y, width, ROW - 2);
                g.setColor(t.accent());
                g.fillRoundRect(left, y + 6, 4, ROW - 14, 4, 4);
            }
            int baseline = y + 21;
            g.setFont(Theme.mono(12.5f));
            g.setColor(t.sub());
            g.drawString(String.format("%2d", i + 1), left + 18, baseline);
            g.setFont(i == selected ? Theme.bold(15f) : Theme.ui(15f));
            g.setColor(t.text());
            g.drawString(lesson.title(), left + 50, baseline);
            int x = left + width * 34 / 100;
            if (lesson.isMix()) {
                g.setFont(Theme.ui(14f));
                g.setColor(t.sub());
                g.drawString(lesson.kind() == Lesson.Kind.RANDOM_MIX
                        ? "tasks from every lesson, at random"
                        : "tasks from your weakest lessons", x, baseline);
            }
            for (Lesson.Key key : lesson.keys()) {
                if (x + Paint.keycapWidth(g, key.key(), 11.5f) > bestX - 16) {
                    break;
                }
                x += Paint.keycap(g, key.key(), x, baseline - 1, 11.5f) + 6;
            }

            OptionalDouble efficiency = app.history().bestEfficiency(lesson.id());
            if (locked) {
                g.setComposite(normal);
                g.setFont(Theme.ui(14f));
                g.setColor(t.sub());
                g.drawString("locked", bestX, baseline);
            } else if (app.history().guidedOnly(lesson.id())) {
                g.setFont(Theme.ui(14f));
                g.setColor(t.sub());
                g.drawString("guided only", bestX, baseline);
            } else if (efficiency.isPresent()) {
                g.setFont(Theme.bold(15f));
                g.setColor(t.text());
                g.drawString(Math.round(efficiency.getAsDouble()) + "%", bestX, baseline);
                g.setFont(Theme.ui(14f));
                g.setColor(t.sub());
                g.drawString(ResultView.seconds(app.history().bestSeconds(lesson.id())
                        .getAsDouble()), bestX + 62, baseline);
            } else {
                g.setFont(Theme.ui(14f));
                g.setColor(t.sub());
                g.drawString("not tried yet", bestX, baseline);
            }
        }
        g.setComposite(normal);
    }
}
