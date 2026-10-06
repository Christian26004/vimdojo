package vimdojo;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import javax.swing.JComponent;

/** The list of lessons with your best result for each. Navigated the Vim way, with j and k. */
final class LessonsView extends JComponent {
    private static final int ROW = 32;
    private static final int TOP = 16;

    private final App app;
    private int selected;

    LessonsView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_J, KeyEvent.VK_DOWN -> select(selected + 1);
                    case KeyEvent.VK_K, KeyEvent.VK_UP -> select(selected - 1);
                    case KeyEvent.VK_G -> select(e.isShiftDown() ? Lessons.ALL.size() - 1 : 0);
                    case KeyEvent.VK_ENTER -> app.startLesson(selected);
                    default -> {
                    }
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = (e.getY() - TOP + offset()) / ROW;
                if (e.getY() >= TOP && row >= 0 && row < Lessons.ALL.size()) {
                    app.startLesson(row);
                }
            }
        });
    }

    void open(int lesson) {
        selected = lesson;
        repaint();
    }

    private void select(int index) {
        selected = Math.max(0, Math.min(index, Lessons.ALL.size() - 1));
        repaint();
    }

    /** Scrolls just enough to keep the selected row on screen in a short window. */
    private int offset() {
        int visible = Math.max(ROW, getHeight() - TOP - 50);
        return Math.max(0, (selected + 1) * ROW - visible);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        int width = Math.min(getWidth() - 80, 1000);
        int left = (getWidth() - width) / 2;
        g.clipRect(0, 0, getWidth(), getHeight() - 44);
        for (int i = 0; i < Lessons.ALL.size(); i++) {
            Lesson lesson = Lessons.ALL.get(i);
            int y = TOP + i * ROW - offset();
            if (i == selected) {
                g.setColor(t.subAlt());
                g.fillRoundRect(left, y, width, ROW - 2, 10, 10);
            }
            int baseline = y + 21;
            g.setFont(Theme.font(15f));
            g.setColor(t.sub());
            g.drawString(String.format("%2d", i + 1), left + 12, baseline);
            g.setColor(i == selected ? t.main() : t.text());
            g.drawString(lesson.title(), left + 52, baseline);
            g.setColor(t.sub());
            String keys = lesson.keys().stream().map(Lesson.Key::key)
                    .collect(Collectors.joining("  "));
            g.drawString(keys, left + width * 34 / 100, baseline);

            OptionalDouble efficiency = app.history().bestEfficiency(lesson.id());
            OptionalDouble seconds = app.history().bestSeconds(lesson.id());
            if (efficiency.isPresent()) {
                g.setColor(t.main());
                g.drawString(Math.round(efficiency.getAsDouble()) + "%", left + width * 80 / 100,
                        baseline);
                g.setColor(t.text());
                g.drawString(ResultView.seconds(seconds.getAsDouble()), left + width * 89 / 100,
                        baseline);
            } else {
                g.drawString("not tried yet", left + width * 80 / 100, baseline);
            }
        }
        g.setClip(null);
        g.setFont(Theme.font(13f));
        g.setColor(t.sub());
        Paint.centered(g, "j k  -  move      enter  -  start      best efficiency and time on the right",
                getWidth(), getHeight() - 28);
    }
}
