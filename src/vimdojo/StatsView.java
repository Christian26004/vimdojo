package vimdojo;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.swing.JComponent;

/** Everything recorded so far: totals, bests per lesson, a trend line and recent runs. */
final class StatsView extends JComponent {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy  HH:mm", Locale.ENGLISH);
    private static final int ROW = 26;
    private static final int TREND = 50;
    private static final int COLUMNS = 4;

    private final App app;
    private int scroll;
    private int contentHeight;

    StatsView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE -> app.startLesson(app.lessonIndex());
                    case KeyEvent.VK_J, KeyEvent.VK_DOWN -> scrollBy(ROW * 3);
                    case KeyEvent.VK_K, KeyEvent.VK_UP -> scrollBy(-ROW * 3);
                    default -> {
                    }
                }
            }
        });
        addMouseWheelListener(e -> scrollBy((int) Math.round(e.getPreciseWheelRotation() * ROW)));
    }

    private void scrollBy(int delta) {
        scroll = Math.max(0, Math.min(scroll + delta, contentHeight - getHeight()));
        repaint();
    }

    void reset() {
        scroll = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        List<Attempt> all = app.history().all();
        int width = Math.min(getWidth() - 80, 1000);
        int left = (getWidth() - width) / 2;

        if (all.isEmpty()) {
            g.setFont(Theme.font(18f));
            g.setColor(t.sub());
            Paint.centered(g, "no lessons finished yet  -  press enter to start one", getWidth(),
                    getHeight() / 2);
            return;
        }

        g.translate(0, -scroll);
        int y = 24;

        long learned = all.stream().map(Attempt::lesson).distinct().count();
        List<Attempt> lastTen = all.subList(Math.max(0, all.size() - 10), all.size());
        String[][] totals = {
            {"lessons learned", learned + "/" + Lessons.ALL.size()},
            {"runs", Integer.toString(all.size())},
            {"time practising", duration(all.stream().mapToDouble(Attempt::seconds).sum())},
            {"keys pressed", Integer.toString(all.stream().mapToInt(Attempt::keys).sum())},
            {"efficiency (last 10)", Math.round(lastTen.stream()
                    .mapToDouble(Attempt::efficiency).average().orElse(0)) + "%"},
        };
        for (int i = 0; i < totals.length; i++) {
            stat(g, totals[i][0], totals[i][1], left + width * i / totals.length, y);
        }
        y += 96;

        y = heading(g, "personal bests", left, y);
        for (int i = 0; i < Lessons.ALL.size(); i++) {
            Lesson lesson = Lessons.ALL.get(i);
            int x = left + width * (i % COLUMNS) / COLUMNS;
            int cellY = y + (i / COLUMNS) * 84;
            OptionalDouble efficiency = app.history().bestEfficiency(lesson.id());
            stat(g, lesson.title(), efficiency.isPresent()
                    ? Math.round(efficiency.getAsDouble()) + "%" : "-", x, cellY);
            if (efficiency.isPresent()) {
                g.setFont(Theme.font(12f));
                g.setColor(t.sub());
                g.drawString(ResultView.seconds(app.history().bestSeconds(lesson.id())
                        .getAsDouble()) + " fastest, " + app.history().runs(lesson.id())
                        + (app.history().runs(lesson.id()) == 1 ? " run" : " runs"), x, cellY + 66);
            }
        }
        y += (Lessons.ALL.size() + COLUMNS - 1) / COLUMNS * 84 + 12;

        List<Attempt> trend = all.subList(Math.max(0, all.size() - TREND), all.size());
        if (trend.size() > 1) {
            y = heading(g, "efficiency, last " + trend.size() + " runs", left, y);
            y = paintTrend(g, trend, left, y, width, 120) + 34;
        }

        y = heading(g, "recent runs", left, y);
        String[] headers = {"efficiency", "time", "keys", "par", "lesson", "date"};
        int[] columns = {0, 14, 26, 36, 46, 76};
        g.setFont(Theme.font(12f));
        g.setColor(t.sub());
        for (int c = 0; c < headers.length; c++) {
            g.drawString(headers[c], left + 12 + width * columns[c] / 100, y);
        }
        y += 10;
        g.setFont(Theme.font(14f));
        for (int i = all.size() - 1; i >= 0; i--) {
            Attempt a = all.get(i);
            if ((all.size() - 1 - i) % 2 == 0) {
                g.setColor(t.subAlt());
                g.fillRoundRect(left, y, width, ROW, 8, 8);
            }
            String title = Lessons.ALL.stream().filter(l -> l.id().equals(a.lesson()))
                    .map(Lesson::title).findFirst().orElse(a.lesson());
            String[] cells = {Math.round(a.efficiency()) + "%", ResultView.seconds(a.seconds()),
                    Integer.toString(a.keys()), Integer.toString(a.par()), title,
                    DATE.format(Instant.ofEpochMilli(a.timestamp()).atZone(ZoneId.systemDefault()))};
            for (int c = 0; c < cells.length; c++) {
                g.setColor(c == 0 ? t.main() : t.text());
                g.drawString(cells[c], left + 12 + width * columns[c] / 100, y + 18);
            }
            y += ROW;
        }
        contentHeight = y + 30;
    }

    private void stat(Graphics2D g, String label, String value, int x, int y) {
        Theme t = Theme.current();
        g.setFont(Theme.font(13f));
        g.setColor(t.sub());
        g.drawString(label, x, y + 14);
        g.setFont(Theme.font(30f));
        g.setColor(t.main());
        g.drawString(value, x, y + 48);
    }

    private int heading(Graphics2D g, String text, int x, int y) {
        g.setFont(Theme.font(16f));
        g.setColor(Theme.current().text());
        g.drawString(text, x, y + 14);
        return y + 34;
    }

    /** Returns the y coordinate of the chart's bottom edge. */
    private int paintTrend(Graphics2D g, List<Attempt> trend, int x, int y, int w, int h) {
        Theme t = Theme.current();
        g.setFont(Theme.font(11f));
        FontMetrics fm = g.getFontMetrics();
        int plotX = x + 40;
        int plotW = w - 40;
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 2; i++) {
            int gy = y + h - h * i / 2;
            g.setColor(t.subAlt());
            g.drawLine(plotX, gy, plotX + plotW, gy);
            g.setColor(t.sub());
            String label = 50 * i + "%";
            g.drawString(label, plotX - 8 - fm.stringWidth(label), gy + 4);
        }
        Path2D path = new Path2D.Double();
        g.setColor(t.main());
        for (int i = 0; i < trend.size(); i++) {
            double px = plotX + (double) plotW * i / (trend.size() - 1);
            double py = y + h - h * trend.get(i).efficiency() / 100;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
            g.fillOval((int) Math.round(px) - 3, (int) Math.round(py) - 3, 6, 6);
        }
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        return y + h;
    }

    private static String duration(double seconds) {
        long s = Math.round(seconds);
        return s >= 3600 ? String.format(Locale.ROOT, "%dh %02dm", s / 3600, s % 3600 / 60)
                : String.format(Locale.ROOT, "%dm %02ds", s / 60, s % 60);
    }
}
