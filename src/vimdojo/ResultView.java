package vimdojo;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Arc2D;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.swing.JComponent;

/** Summary of the lesson just finished: an efficiency ring, the numbers and a per-task chart. */
final class ResultView extends JComponent {
    private static final int SOLUTION_ROW = 30;

    private final App app;
    private Run run;
    private Attempt attempt;
    private OptionalDouble previousEfficiency = OptionalDouble.empty();
    private OptionalDouble previousSeconds = OptionalDouble.empty();
    private int scroll;
    private int overflow;
    // The task row picked for replaying, and where each row was last drawn, for the mouse.
    private int picked;
    private final List<Rectangle> rowAreas = new ArrayList<>();

    ResultView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        // Only needed when the window is too short to show the whole summary.
        addMouseWheelListener(e -> {
            scroll = Math.max(0, Math.min(overflow,
                    scroll + (int) Math.round(e.getPreciseWheelRotation() * 30)));
            repaint();
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> app.startLesson(app.lessonIndex() + 1);
                    case KeyEvent.VK_TAB -> app.startLesson(app.lessonIndex());
                    case KeyEvent.VK_DOWN -> pick(picked + 1);
                    case KeyEvent.VK_UP -> pick(picked - 1);
                    default -> {
                    }
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                switch (e.getKeyChar()) {
                    case 'j' -> pick(picked + 1);
                    case 'k' -> pick(picked - 1);
                    case 'r', ' ' -> app.replay(picked);
                    default -> {
                    }
                }
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int row = rowAt(e);
                if (row >= 0) {
                    pick(row);
                    app.replay(row);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(rowAt(e) >= 0 ? Cursor.HAND_CURSOR
                        : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private int rowAt(MouseEvent e) {
        for (int i = 0; i < rowAreas.size(); i++) {
            if (rowAreas.get(i).contains(e.getPoint())) {
                return i;
            }
        }
        return -1;
    }

    private void pick(int row) {
        if (run != null) {
            picked = Math.max(0, Math.min(row, run.tasks().size() - 1));
            repaint();
        }
    }

    void show(Run run, Attempt attempt, OptionalDouble previousEfficiency,
              OptionalDouble previousSeconds) {
        this.run = run;
        this.attempt = attempt;
        this.previousEfficiency = previousEfficiency;
        this.previousSeconds = previousSeconds;
        scroll = 0;
        picked = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (run == null) {
            return;
        }
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        int width = Math.min(getWidth() - 80, 920);
        int left = (getWidth() - width) / 2;
        // The par solutions go in two columns when there is room for them side by side.
        int tasks = run.tasks().size();
        int columns = width >= 820 ? 2 : 1;
        int solutionRows = (tasks + columns - 1) / columns;
        int total = 416 + solutionRows * SOLUTION_ROW + 6;
        overflow = Math.max(0, total + 16 - getHeight());
        scroll = Math.min(scroll, overflow);
        int top = overflow > 0 ? 8 - scroll : (getHeight() - total) / 2;

        Paint.label(g, "lesson " + (app.lessonIndex() + 1) + " complete", left, top + 12);
        g.setFont(Theme.bold(30f));
        g.setColor(t.text());
        g.drawString(run.lesson().title(), left - 1, top + 50);

        // Efficiency as a ring that fills clockwise from the top.
        int ring = 150;
        int ringTop = top + 84;
        g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(t.panel());
        g.drawOval(left + 6, ringTop + 6, ring - 12, ring - 12);
        // The ring is full at 100%; beyond that it turns green and the number carries the rest.
        g.setColor(attempt.efficiency() > 100 ? t.good() : t.accent());
        g.draw(new Arc2D.Double(left + 6, ringTop + 6, ring - 12, ring - 12, 90,
                -360 * Math.min(100, attempt.efficiency()) / 100, Arc2D.OPEN));
        g.setFont(Theme.bold(36f));
        g.setColor(t.text());
        String percent = Math.round(attempt.efficiency()) + "%";
        g.drawString(percent, left + (ring - g.getFontMetrics().stringWidth(percent)) / 2,
                ringTop + ring / 2 + 8);
        g.setFont(Theme.caps(9.5f));
        g.setColor(t.sub());
        String caption = "EFFICIENCY";
        g.drawString(caption, left + (ring - g.getFontMetrics().stringWidth(caption)) / 2 + 1,
                ringTop + ring / 2 + 28);

        int column = left + ring + 44;
        figure(g, "time", seconds(attempt.seconds()), "", column, ringTop + 6);
        figure(g, "keystrokes", Integer.toString(attempt.keys()), "par " + attempt.par()
                + (attempt.keys() < attempt.par() ? ", " + (attempt.par() - attempt.keys())
                + " under" : ""), column, ringTop + 84);

        paintChart(g, left + 400, ringTop - 8, width - 400, 170);

        int recapY = ringTop + ring + 50;
        if (run.lesson().keys().isEmpty()) {
            Paint.label(g, "no hints, your own choice of keys", left, recapY);
        } else {
            int x = left + Paint.label(g, "practiced", left, recapY) + 16;
            for (Lesson.Key key : run.lesson().keys()) {
                x += Paint.keycap(g, key.key(), x, recapY + 1, 13f) + 8;
            }
        }

        g.setFont(Theme.ui(15f));
        g.setColor(t.sub());
        String best;
        if (previousEfficiency.isEmpty()) {
            best = "First run of this lesson.";
        } else {
            boolean cleaner = attempt.efficiency() > previousEfficiency.getAsDouble() + 0.05;
            boolean faster = attempt.seconds() < previousSeconds.getAsDouble();
            if (cleaner || faster) {
                g.setFont(Theme.bold(15f));
                g.setColor(t.accent());
                best = cleaner && faster ? "New best efficiency and time."
                        : cleaner ? "New best efficiency." : "New best time.";
            } else {
                best = String.format(Locale.ROOT, "Your best is %d%% in %s.",
                        Math.round(previousEfficiency.getAsDouble()),
                        seconds(previousSeconds.getAsDouble()));
            }
        }
        g.drawString(best, left, recapY + 40);

        // Whether this run opened the next lesson, or what it still takes.
        int next = app.lessonIndex() + 1;
        if (!run.lesson().isMix() && next < Lessons.LESSONS.size()) {
            boolean open = app.unlocked(next);
            boolean justNow = open && attempt.efficiency() >= App.PASS
                    && previousEfficiency.orElse(0) < App.PASS;
            g.setFont(justNow ? Theme.bold(15f) : Theme.ui(15f));
            g.setColor(justNow ? t.good() : t.sub());
            g.drawString(justNow ? "Lesson " + (next + 1) + " is now open."
                    : open ? "" : "Reach " + Math.round(App.PASS) + "% efficiency to open lesson "
                    + (next + 1) + ".", left, recapY + 66);
        }

        // What par looked like for each task, so a wasteful answer can be compared with it.
        int solutionsTop = recapY + 108;
        int labelEnd = left + Paint.label(g, "par for each task", left, solutionsTop);
        g.setFont(Theme.ui(12f));
        g.setColor(t.sub());
        g.drawString("click one to watch it", labelEnd + 14, solutionsTop);
        rowAreas.clear();
        for (int i = 0; i < tasks; i++) {
            int x = left + (i / solutionRows) * (width / 2);
            int baseline = solutionsTop + 30 + (i % solutionRows) * SOLUTION_ROW;
            Rectangle area = new Rectangle(x - 8, baseline - 20, width / columns - 8,
                    SOLUTION_ROW - 2);
            rowAreas.add(area);
            if (i == picked) {
                Paint.panel(g, area.x, area.y, area.width, area.height);
            }
            int used = run.keys(i);
            int par = run.tasks().get(i).par();
            g.setFont(Theme.mono(12.5f));
            g.setColor(t.sub());
            g.drawString(String.format("%2d", i + 1), x, baseline);
            int after = x + 34 + Paint.sequence(g, run.tasks().get(i).solution(), x + 34, baseline,
                    12.5f);
            g.setFont(Theme.ui(13f));
            g.setColor(used > par ? t.accent() : used < par ? t.good() : t.sub());
            g.drawString(used == par ? "matched" : used > par ? "you used " + used
                    : "you used " + used + ", " + (par - used) + " under", after + 8, baseline);
        }
    }

    private void figure(Graphics2D g, String label, String value, String note, int x, int y) {
        Theme t = Theme.current();
        Paint.label(g, label, x, y + 10);
        g.setFont(Theme.bold(30f));
        g.setColor(t.text());
        g.drawString(value, x - 1, y + 46);
        int valueWidth = g.getFontMetrics().stringWidth(value);
        g.setFont(Theme.ui(14f));
        g.setColor(t.sub());
        g.drawString(note, x + valueWidth + 10, y + 46);
    }

    /**
     * One bar per task: keystrokes used, with anything over par in the accent color, and for a
     * task finished under par, the keys saved in green on top.
     */
    private void paintChart(Graphics2D g, int x, int y, int w, int h) {
        Theme t = Theme.current();
        Paint.panel(g, x, y, w, h + 62);
        int plotX = x + 24;
        int plotW = w - 48;
        int plotY = y + 44;
        int plotH = h - 44;
        int n = run.tasks().size();
        int peak = 1;
        for (int i = 0; i < n; i++) {
            peak = Math.max(peak, Math.max(run.keys(i), run.tasks().get(i).par()));
        }

        int titleWidth = Paint.label(g, "keys per task", plotX, y + 26);
        g.setFont(Theme.ui(11.5f));
        FontMetrics fm = g.getFontMetrics();
        Object[][] legend = {{"under par", t.good()}, {"over par", t.accent()},
                {"within par", t.text()}};
        int legendWidth = -18;
        for (Object[] entry : legend) {
            legendWidth += fm.stringWidth((String) entry[0]) + 16 + 18;
        }
        // Beside the title when there is room, otherwise on a line of its own beneath it.
        boolean below = titleWidth + 24 + legendWidth > plotW;
        int legendY = below ? y + 46 : y + 26;
        int legendX = below ? plotX + legendWidth : x + w - 24;
        if (below) {
            plotY += 18;
            plotH -= 18;
        }
        for (Object[] entry : legend) {
            legendX -= fm.stringWidth((String) entry[0]);
            g.setColor(t.sub());
            g.drawString((String) entry[0], legendX, legendY);
            legendX -= 16;
            g.setColor((Color) entry[1]);
            g.fillRoundRect(legendX, legendY - 9, 10, 10, 4, 4);
            legendX -= 18;
        }

        int slot = plotW / n;
        int bar = Math.min(40, slot * 6 / 10);
        for (int i = 0; i < n; i++) {
            int used = run.keys(i);
            int par = run.tasks().get(i).par();
            int bx = plotX + slot * i + (slot - bar) / 2;
            // The bar reaches the larger of the two; its lower part is always the keys in common.
            int fullH = Math.max(4, (plotH - 18) * Math.max(used, par) / peak);
            int baseH = Math.max(4, (plotH - 18) * Math.min(used, par) / peak);
            if (used != par) {
                g.setColor(used > par ? t.accent() : t.good());
                g.fillRoundRect(bx, plotY + plotH - fullH, bar, fullH, 8, 8);
            }
            g.setColor(t.text());
            g.fillRoundRect(bx, plotY + plotH - baseH, bar, baseH, 8, 8);
            if (used != par) {
                // Square off the join between the two colors.
                g.fillRect(bx, plotY + plotH - baseH, bar, Math.min(baseH, 8));
            }
            g.setFont(Theme.bold(11.5f));
            g.setColor(used > par ? t.accent() : used < par ? t.good() : t.text());
            String count = Integer.toString(used);
            g.drawString(count, bx + (bar - g.getFontMetrics().stringWidth(count)) / 2,
                    plotY + plotH - fullH - 6);
            g.setFont(Theme.ui(11.5f));
            g.setColor(t.sub());
            String time = seconds(run.seconds(i));
            g.drawString(time, bx + (bar - fm.stringWidth(time)) / 2, plotY + plotH + 20);
        }
        g.setColor(t.sub());
        Paint.label(g, "time on each task", plotX, y + h + 44);
    }

    static String seconds(double seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }
}
