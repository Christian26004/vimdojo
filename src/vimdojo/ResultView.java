package vimdojo;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.swing.JComponent;

/** Summary of the lesson just finished: headline numbers, a per-task chart and the breakdown. */
final class ResultView extends JComponent {
    private final App app;
    private Run run;
    private Attempt attempt;
    private OptionalDouble previousEfficiency = OptionalDouble.empty();
    private OptionalDouble previousSeconds = OptionalDouble.empty();

    ResultView(App app) {
        this.app = app;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> app.startLesson(app.lessonIndex() + 1);
                    case KeyEvent.VK_TAB -> app.startLesson(app.lessonIndex());
                    default -> {
                    }
                }
            }
        });
    }

    void show(Run run, Attempt attempt, OptionalDouble previousEfficiency,
              OptionalDouble previousSeconds) {
        this.run = run;
        this.attempt = attempt;
        this.previousEfficiency = previousEfficiency;
        this.previousSeconds = previousSeconds;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (run == null) {
            return;
        }
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        int width = Math.min(getWidth() - 80, 1000);
        int left = (getWidth() - width) / 2;
        int top = Math.max(20, (getHeight() - 470) / 2);

        headline(g, "efficiency", Math.round(attempt.efficiency()) + "%", left, top);
        headline(g, "time", seconds(attempt.seconds()), left, top + 110);
        paintChart(g, left + 240, top, width - 240, 220);

        String[][] stats = {
            {"lesson", run.lesson().title()},
            {"keystrokes", attempt.keys() + "/" + attempt.par()},
            {"tasks", Integer.toString(attempt.tasks())},
            {"keys per minute", attempt.seconds() == 0 ? "-"
                    : Long.toString(Math.round(attempt.keys() * 60 / attempt.seconds()))},
        };
        int[] columns = {0, 36, 58, 74};
        for (int i = 0; i < stats.length; i++) {
            int x = left + width * columns[i] / 100;
            g.setFont(Theme.font(14f));
            g.setColor(t.sub());
            g.drawString(stats[i][0], x, top + 280);
            g.setFont(Theme.font(i == 0 ? 16f : 22f));
            g.setColor(t.main());
            g.drawString(stats[i][1], x, top + 312);
        }
        g.setFont(Theme.font(11f));
        g.setColor(t.sub());
        g.drawString("yours/par", left + width * columns[1] / 100, top + 332);

        // What this lesson taught, as a recap.
        StringBuilder learned = new StringBuilder("practised   ");
        for (Lesson.Key key : run.lesson().keys()) {
            learned.append('`').append(key.key()).append("`   ");
        }
        g.setFont(Theme.font(15f));
        Paint.rich(g, learned.toString(), left, top + 376, t.sub(), t.main());

        g.setFont(Theme.font(15f));
        g.setColor(t.sub());
        String best;
        if (previousEfficiency.isEmpty()) {
            best = "first run of this lesson";
        } else {
            boolean cleaner = attempt.efficiency() > previousEfficiency.getAsDouble() + 0.05;
            boolean faster = attempt.seconds() < previousSeconds.getAsDouble();
            if (cleaner || faster) {
                g.setColor(t.main());
                best = cleaner && faster ? "new best efficiency and time"
                        : cleaner ? "new best efficiency" : "new best time";
            } else {
                best = String.format(Locale.ROOT, "personal best  %d%%  %s",
                        Math.round(previousEfficiency.getAsDouble()),
                        seconds(previousSeconds.getAsDouble()));
            }
        }
        g.drawString(best, left, top + 412);

        g.setFont(Theme.font(13f));
        g.setColor(t.sub());
        boolean last = app.lessonIndex() == Lessons.ALL.size() - 1;
        Paint.centered(g, (last ? "" : "enter  -  next lesson      ") + "tab  -  try again",
                getWidth(), getHeight() - 28);
    }

    private void headline(Graphics2D g, String label, String value, int x, int y) {
        Theme t = Theme.current();
        g.setFont(Theme.font(24f));
        g.setColor(t.sub());
        g.drawString(label, x, y + 24);
        g.setFont(Theme.font(56f));
        g.setColor(t.main());
        g.drawString(value, x - 3, y + 82);
    }

    /** One bar per task: keystrokes used, with anything over par in the error colour. */
    private void paintChart(Graphics2D g, int x, int y, int w, int h) {
        Theme t = Theme.current();
        int plotX = x + 34;
        int plotY = y + 8;
        int plotW = w - 34;
        int plotH = h - 8 - 62;
        int n = run.tasks().size();
        int peak = 1;
        for (int i = 0; i < n; i++) {
            peak = Math.max(peak, Math.max(run.keys(i), run.tasks().get(i).par()));
        }
        int step = Math.max(1, (int) Math.ceil(peak / 4.0));
        int yMax = step * 4;

        g.setFont(Theme.font(11f));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i <= 4; i++) {
            int gy = plotY + plotH - plotH * i / 4;
            g.setColor(t.subAlt());
            g.drawLine(plotX, gy, plotX + plotW, gy);
            g.setColor(t.sub());
            String label = Integer.toString(step * i);
            g.drawString(label, plotX - 8 - fm.stringWidth(label), gy + 4);
        }

        int slot = plotW / n;
        int bar = Math.min(46, slot * 6 / 10);
        for (int i = 0; i < n; i++) {
            int used = run.keys(i);
            int par = run.tasks().get(i).par();
            int bx = plotX + slot * i + (slot - bar) / 2;
            int usedH = plotH * used / yMax;
            int parH = plotH * Math.min(used, par) / yMax;
            g.setColor(t.error());
            g.fillRoundRect(bx, plotY + plotH - usedH, bar, usedH, 6, 6);
            g.setColor(t.main());
            g.fillRoundRect(bx, plotY + plotH - parH, bar, parH, 6, 6);
            if (used > par) {
                // Square off the join between the two colours.
                g.fillRect(bx, plotY + plotH - parH, bar, Math.min(parH, 6));
            }
            g.setColor(t.sub());
            String number = Integer.toString(i + 1);
            g.drawString(number, bx + (bar - fm.stringWidth(number)) / 2, plotY + plotH + 16);
            String time = seconds(run.seconds(i));
            g.drawString(time, bx + (bar - fm.stringWidth(time)) / 2, plotY + plotH + 32);
        }

        int legendY = y + h - 4;
        int legendX = plotX;
        g.setColor(t.main());
        g.fillRect(legendX, legendY - 8, 10, 8);
        g.drawString("keys within par", legendX + 16, legendY);
        legendX += 16 + fm.stringWidth("keys within par") + 22;
        g.setColor(t.error());
        g.fillRect(legendX, legendY - 8, 10, 8);
        g.drawString("extra keys", legendX + 16, legendY);
        g.setColor(t.sub());
        String axis = "task, with time taken";
        g.drawString(axis, plotX + plotW - fm.stringWidth(axis), legendY);
    }

    static String seconds(double seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }
}
