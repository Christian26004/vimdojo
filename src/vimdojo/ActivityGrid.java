package vimdojo;

import java.awt.Graphics2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Draws the activity calendar: a year of days with the streak figures beside it. */
final class ActivityGrid {
    private static final int WEEKS = 53;
    private static final int FIGURES_WIDTH = 142;
    private static final int[] SHADES = {0, 85, 140, 195, 255};

    private ActivityGrid() {
    }

    /**
     * One square per day, shaded by how many lessons were finished on it, fitted to the given
     * width. Returns the y coordinate of the bottom edge.
     */
    static int paint(Graphics2D g, Activity activity, int left, int y, int width) {
        Theme t = Theme.current();
        LocalDate today = activity.today();

        int labelWidth = Paint.label(g, "activity", left, y);
        g.setFont(Theme.ui(13.5f));
        g.setColor(activity.practicedToday() ? t.text() : t.accent());
        g.drawString(activity.nudge(), left + labelWidth + 18, y);

        int gridX = left + 34;
        int gridY = y + 34;
        // The whole year always shows; in a narrow window the squares get smaller to fit.
        int cell = Math.max(8, Math.min(14, (width - 34 - FIGURES_WIDTH) / WEEKS));
        int square = cell - (cell >= 12 ? 3 : 2);
        // Columns are weeks starting on Sunday; the last column is the current week.
        LocalDate first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                .minusWeeks(WEEKS - 1);
        g.setFont(Theme.ui(10.5f));
        for (int week = 0; week < WEEKS; week++) {
            LocalDate sunday = first.plusWeeks(week);
            // Name a month above the first column that starts in it.
            if (sunday.getDayOfMonth() <= 7 && week < WEEKS - 2) {
                g.setColor(t.sub());
                g.drawString(sunday.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        gridX + week * cell, gridY - 7);
            }
            for (int weekday = 0; weekday < 7; weekday++) {
                LocalDate day = sunday.plusDays(weekday);
                if (day.isAfter(today)) {
                    break;
                }
                int level = Activity.level(activity.on(day));
                g.setColor(level == 0 ? t.panel() : t.wash(SHADES[level]));
                g.fillRoundRect(gridX + week * cell, gridY + weekday * cell, square, square,
                        4, 4);
            }
        }
        g.setColor(t.sub());
        for (int weekday : new int[] {1, 3, 5}) {
            g.drawString(new String[] {"", "Mon", "", "Wed", "", "Fri"}[weekday], left,
                    gridY + weekday * cell + 10);
        }

        // Streak figures to the right of the grid.
        int figuresX = gridX + WEEKS * cell + 22;
        String[][] figures = {
            {"current streak", days(activity.currentStreak())},
            {"longest streak", days(activity.longestStreak())},
            {"days practiced", Integer.toString(activity.perDay().size())},
        };
        for (int i = 0; i < figures.length; i++) {
            int figureY = gridY - 6 + i * 36;
            Paint.label(g, figures[i][0], figuresX, figureY + 8);
            g.setFont(Theme.bold(15f));
            g.setColor(t.text());
            g.drawString(figures[i][1], figuresX, figureY + 26);
        }

        // Legend under the grid.
        int legendY = gridY + 7 * cell + 6;
        g.setFont(Theme.ui(10.5f));
        g.setColor(t.sub());
        g.drawString("less", gridX, legendY + 9);
        int x = gridX + g.getFontMetrics().stringWidth("less") + 8;
        for (int level = 0; level <= 4; level++) {
            g.setColor(level == 0 ? t.panel() : t.wash(SHADES[level]));
            g.fillRoundRect(x, legendY, square, square, 4, 4);
            x += cell;
        }
        g.setColor(t.sub());
        g.drawString("more", x + 5, legendY + 9);
        return legendY + cell;
    }

    private static String days(int n) {
        return n + (n == 1 ? " day" : " days");
    }
}
