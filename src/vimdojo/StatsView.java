package vimdojo;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.swing.JComponent;

/**
 * Everything recorded so far: totals, a calendar of the days you practiced, bests per lesson, a
 * trend line and recent runs.
 */
final class StatsView extends JComponent {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final int ROW = 30;
    private static final int TREND = 50;
    private static final int COLUMNS = 4;
    private static final int GAP = 14;

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
                    case KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE -> app.showLesson();
                    case KeyEvent.VK_DOWN -> scrollBy(ROW * 3);
                    case KeyEvent.VK_UP -> scrollBy(-ROW * 3);
                    default -> {
                    }
                }
            }

            // Letters are read as typed characters so they follow the keyboard layout setting.
            @Override
            public void keyTyped(KeyEvent e) {
                switch (e.getKeyChar()) {
                    case 'j' -> scrollBy(ROW * 3);
                    case 'k' -> scrollBy(-ROW * 3);
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

    void jump(boolean top) {
        scrollBy(top ? -contentHeight : contentHeight);
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
        int width = Math.min(getWidth() - 80, 920);
        int left = (getWidth() - width) / 2;

        if (all.isEmpty()) {
            g.setFont(Theme.ui(18f));
            g.setColor(t.sub());
            Paint.centered(g, "No lessons finished yet. Press enter to start one.", getWidth(),
                    getHeight() / 2);
            return;
        }

        g.translate(0, -scroll);
        int y = 12;

        // Totals, one tile each.
        long learned = all.stream().map(Attempt::lesson).distinct()
                .filter(id -> Lessons.byId(id) != null && !Lessons.byId(id).isMix()).count();
        List<Attempt> lastTen = all.subList(Math.max(0, all.size() - 10), all.size());
        String[][] totals = {
            {"lessons learned", learned + " of " + Lessons.LESSONS.size()},
            {"runs", Integer.toString(all.size())},
            {"time practicing", duration(all.stream().mapToDouble(Attempt::seconds).sum())},
            {"keys pressed", String.format(Locale.ENGLISH, "%,d",
                    all.stream().mapToInt(Attempt::keys).sum())},
            {"efficiency, last 10", Math.round(lastTen.stream()
                    .mapToDouble(Attempt::efficiency).average().orElse(0)) + "%"},
        };
        // As many tiles per row as fit at a readable width; the rest wrap to the next row.
        int perRow = Math.max(2, Math.min(totals.length, (width + GAP) / (172 + GAP)));
        int tile = (width - GAP * (perRow - 1)) / perRow;
        for (int i = 0; i < totals.length; i++) {
            int x = left + (i % perRow) * (tile + GAP);
            int tileY = y + (i / perRow) * (84 + GAP);
            Paint.panel(g, x, tileY, tile, 84);
            Paint.label(g, totals[i][0], x + 16, tileY + 28);
            g.setFont(Theme.bold(26f));
            g.setColor(t.text());
            g.drawString(totals[i][1], x + 15, tileY + 64);
        }
        y += (totals.length + perRow - 1) / perRow * (84 + GAP) - GAP + 36;

        y = ActivityGrid.paint(g, Activity.of(all, LocalDate.now(), ZoneId.systemDefault()), left,
                y, width) + 34;

        // Best efficiency per lesson, as a bar that fills toward 100%.
        Paint.label(g, "personal bests", left, y);
        y += 20;
        int perBestRow = Math.max(2, Math.min(COLUMNS, (width + GAP * 2) / (205 + GAP * 2)));
        int cell = (width - GAP * 2 * (perBestRow - 1)) / perBestRow;
        for (int i = 0; i < Lessons.ALL.size(); i++) {
            Lesson lesson = Lessons.ALL.get(i);
            int x = left + (i % perBestRow) * (cell + GAP * 2);
            int cellY = y + (i / perBestRow) * 66;
            OptionalDouble efficiency = app.history().bestEfficiency(lesson.id());
            g.setFont(Theme.ui(14f));
            g.setColor(efficiency.isPresent() ? t.text() : t.sub());
            g.drawString(lesson.title(), x, cellY + 14);
            g.setColor(t.panel());
            g.fillRoundRect(x, cellY + 24, cell, 6, 6, 6);
            if (efficiency.isPresent()) {
                String percent = Math.round(efficiency.getAsDouble()) + "%";
                g.setFont(Theme.bold(14f));
                g.setColor(t.text());
                g.drawString(percent, x + cell - g.getFontMetrics().stringWidth(percent),
                        cellY + 14);
                // The bar is full at 100%; a best beyond that shows in green.
                g.setColor(efficiency.getAsDouble() > 100 ? t.good() : t.accent());
                g.fillRoundRect(x, cellY + 24,
                        (int) (cell * Math.min(100, efficiency.getAsDouble()) / 100), 6, 6, 6);
                long runs = app.history().runs(lesson.id());
                g.setFont(Theme.ui(12f));
                g.setColor(t.sub());
                g.drawString(ResultView.seconds(app.history().bestSeconds(lesson.id())
                        .getAsDouble()) + " fastest, " + runs + (runs == 1 ? " run" : " runs"),
                        x, cellY + 47);
            }
        }
        y += (Lessons.ALL.size() + perBestRow - 1) / perBestRow * 66 + 20;

        List<Attempt> trend = all.subList(Math.max(0, all.size() - TREND), all.size());
        if (trend.size() > 1) {
            Paint.label(g, "efficiency over your last " + trend.size() + " runs", left, y);
            y = paintTrend(g, trend, left, y + 18, width, 110) + 42;
        }

        Paint.label(g, "recent runs", left, y);
        y += 16;
        String[] headers = {"lesson", "efficiency", "time", "keys", "par", "when"};
        int[] columns = {0, 34, 48, 59, 68, 77};
        for (int c = 0; c < headers.length; c++) {
            Paint.label(g, headers[c], left + 14 + width * columns[c] / 100, y + 14);
        }
        y += 24;
        for (int i = all.size() - 1; i >= 0; i--) {
            Attempt a = all.get(i);
            if ((all.size() - 1 - i) % 2 == 0) {
                Paint.panel(g, left, y, width, ROW);
            }
            String title = Lessons.ALL.stream().filter(l -> l.id().equals(a.lesson()))
                    .map(Lesson::title).findFirst().orElse(a.lesson());
            String[] cells = {title, Math.round(a.efficiency()) + "%",
                    ResultView.seconds(a.seconds()), Integer.toString(a.keys()),
                    Integer.toString(a.par()),
                    DATE.format(Instant.ofEpochMilli(a.timestamp()).atZone(ZoneId.systemDefault()))};
            for (int c = 0; c < cells.length; c++) {
                g.setFont(c == 1 ? Theme.bold(14f) : Theme.ui(14f));
                g.setColor(c == 5 ? t.sub() : t.text());
                g.drawString(cells[c], left + 14 + width * columns[c] / 100, y + 20);
            }
            y += ROW;
        }
        contentHeight = y + 24;
    }

    /** Returns the y coordinate of the chart's bottom edge. */
    private int paintTrend(Graphics2D g, List<Attempt> trend, int x, int y, int w, int h) {
        Theme t = Theme.current();
        g.setFont(Theme.ui(11.5f));
        FontMetrics fm = g.getFontMetrics();
        int plotX = x + 44;
        int plotW = w - 44 - 6;
        // The scale stops at 100% unless a run shown here beat par; then it grows to the next
        // multiple of 20, so the halfway line is a round number too. Three lines only: the
        // bottom, the top, and halfway between them.
        double best = trend.stream().mapToDouble(Attempt::efficiency).max().orElse(0);
        int top = best <= 100 ? 100 : (int) Math.ceil(best / 20) * 20;
        g.setStroke(new BasicStroke(1f));
        for (int mark : new int[] {0, top / 2, top}) {
            int gy = y + h - h * mark / top;
            g.setColor(t.panel());
            g.drawLine(plotX, gy, plotX + plotW, gy);
            g.setColor(mark > 100 ? t.good() : t.sub());
            String label = mark + "%";
            g.drawString(label, plotX - 10 - fm.stringWidth(label), gy + 4);
        }
        Path2D path = new Path2D.Double();
        for (int i = 0; i < trend.size(); i++) {
            double px = plotX + (double) plotW * i / (trend.size() - 1);
            double py = y + h - h * trend.get(i).efficiency() / top;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        g.setColor(t.accent());
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        // Mark the latest run.
        double lastY = y + h - h * trend.get(trend.size() - 1).efficiency() / top;
        g.fillOval(plotX + plotW - 5, (int) Math.round(lastY) - 5, 10, 10);
        return y + h;
    }

    private static String duration(double seconds) {
        long s = Math.round(seconds);
        return s >= 3600 ? String.format(Locale.ROOT, "%dh %02dm", s / 3600, s % 3600 / 60)
                : String.format(Locale.ROOT, "%dm %02ds", s / 60, s % 60);
    }
}
