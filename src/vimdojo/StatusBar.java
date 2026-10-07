package vimdojo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JPanel;

/**
 * The strip along the bottom, modeled on Vim's own status line: the current mode, the keys
 * that work here, any half-typed command, and the links to the other screens.
 */
final class StatusBar extends JPanel {
    static final int HEIGHT = 34;
    private static final int MODE_WIDTH = 100;

    private final App app;
    private final JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final Map<String, Chip> chips = new LinkedHashMap<>();

    StatusBar(App app) {
        super(new BorderLayout());
        this.app = app;
        setOpaque(false);
        setPreferredSize(new Dimension(10, HEIGHT));
        links.setOpaque(false);
        chips.put("docs", new Chip(() -> "docs", app::docsOpen, app::toggleDocs));
        chips.put("lessons", new Chip(() -> "lessons", () -> app.card().equals("lessons"),
                app::showLessons));
        chips.put("stats", new Chip(() -> "stats", () -> app.card().equals("stats"),
                app::showStats));
        chips.put("settings", new Chip(() -> "settings", () -> app.card().equals("settings"),
                app::showSettings));
        chips.values().forEach(links::add);
        add(links, BorderLayout.EAST);
    }

    // Where the parts of the bar are, for the tour to point at.

    Rectangle modeArea() {
        return new Rectangle(0, 0, MODE_WIDTH, getHeight());
    }

    Rectangle hintsArea() {
        return new Rectangle(MODE_WIDTH, 0, Math.max(0, links.getX() - MODE_WIDTH), getHeight());
    }

    Rectangle linksArea() {
        return links.getBounds();
    }

    /** The link to one screen, in this bar's coordinates. */
    Rectangle linkArea(String name) {
        Rectangle r = chips.get(name).getBounds();
        r.translate(links.getX(), links.getY());
        return r;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        g.setColor(t.panel());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (app.dvorak()) {
            // A standing reminder of why the keys aren't where they are printed.
            g.setFont(Theme.caps(9.5f));
            g.setColor(t.accent());
            String layout = "DVORAK";
            g.drawString(layout, links.getX() - g.getFontMetrics().stringWidth(layout) - 14,
                    (getHeight() - g.getFontMetrics().getHeight()) / 2
                            + g.getFontMetrics().getAscent());
        }

        Vim vim = app.run().vim();
        boolean typing = app.card().equals("challenge") && !app.inIntro() && !app.docsOpen();
        String mode;
        Color block = t.sub();
        if (app.command() != null) {
            mode = app.prompt() == '/' ? "search" : "command";
            block = t.accent();
        } else if (app.docsOpen()) {
            mode = "docs";
        } else if (app.replayOpen()) {
            mode = "replay";
        } else if (!typing) {
            mode = app.card().equals("challenge") ? "ready" : app.card();
        } else {
            mode = switch (vim.mode()) {
                case NORMAL -> "normal";
                case INSERT -> "insert";
                case VISUAL -> "visual";
                case VISUAL_LINE -> "v-line";
                case SEARCH -> "search";
            };
            block = vim.mode() == Vim.Mode.NORMAL ? t.text() : t.accent();
        }
        g.setFont(Theme.caps(10.5f));
        FontMetrics fm = g.getFontMetrics();
        int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        int modeWidth = MODE_WIDTH;
        g.setColor(block);
        g.fillRect(0, 0, modeWidth, getHeight());
        g.setColor(t.bg());
        Paint.centered(g, mode.toUpperCase(), modeWidth, baseline);

        int x = modeWidth + 16;
        g.setFont(Theme.ui(12.5f));
        baseline = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
        if (app.command() != null) {
            // The command line takes over the bar, as it does in Vim.
            g.setFont(Theme.mono(13.5f));
            g.setColor(t.text());
            String typed = app.prompt() + app.command();
            g.drawString(typed, x, baseline);
            g.setColor(t.accent());
            g.fillRect(x + g.getFontMetrics().stringWidth(typed) + 1, 9, 2, getHeight() - 18);
            return;
        }
        if (app.message() != null) {
            g.setFont(Theme.bold(12.5f));
            g.setColor(t.accent());
            g.drawString(app.message(), x, baseline);
            return;
        }
        if (app.docsOpen() && !app.docsStatus().isEmpty()) {
            g.setFont(Theme.mono(12.5f));
            g.setColor(t.text());
            g.drawString(app.docsStatus(), x, baseline);
            x += g.getFontMetrics().stringWidth(app.docsStatus()) + 22;
            g.setFont(Theme.ui(12.5f));
        }
        String hints = app.docsOpen()
                ? "`/` search   `n` `N` next match   `q` close   `j` `k` move   `d` `u` scroll text"
                        + "   `space` `b` page"
                : app.replayOpen() ? "`q` close   `j` `k` other tasks"
                : switch (app.card()) {
            case "challenge" -> app.inIntro() ? "`enter` begin   `h` `l` other lessons"
                    : "`tab` restart";
            case "result" -> (app.lessonIndex() == Lessons.ALL.size() - 1 ? "`tab` try again"
                    : "`enter` next lesson   `tab` try again") + "   `j` `k` `r` replay a task";
            case "lessons" -> "`j` `k` move   `enter` start";
            case "settings" -> "`j` `k` move   `h` `l` change   `enter` select";
            default -> "`j` `k` scroll   `enter` back to the lesson";
        }
                + "   `:` command   `gt` next screen";
        // Hints are drawn most useful first and stop where the links begin.
        int limit = links.getX() - (app.dvorak() ? 78 : 16);
        for (String hint : hints.split("   ")) {
            int hintWidth = Paint.proseWidth(g, hint, 12.5f);
            if (x + hintWidth > limit) {
                break;
            }
            Paint.prose(g, hint, x, baseline, 12.5f, t.sub());
            x += hintWidth + 12;
        }
        x += 14;


        // What Vim itself would show: the search being typed, or a command awaiting its motion.
        if (typing) {
            g.setFont(Theme.mono(13f));
            if (vim.mode() == Vim.Mode.SEARCH) {
                g.setColor(t.text());
                g.drawString("/" + vim.searchText(), x, baseline);
            } else if (!vim.pending().isEmpty()) {
                g.setColor(t.accent());
                g.drawString(vim.pending(), x, baseline);
            }
        }
    }
}
