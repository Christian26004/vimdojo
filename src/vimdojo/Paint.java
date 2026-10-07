package vimdojo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Drawing helpers shared by the screens. */
final class Paint {
    private Paint() {
    }

    static void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Theme.current().panel());
        g.fillRoundRect(x, y, w, h, 16, 16);
    }

    /** A small spaced-out upper-case label. Returns its width. */
    static int label(Graphics2D g, String text, int x, int baseline) {
        g.setFont(Theme.caps(10.5f));
        g.setColor(Theme.current().sub());
        g.drawString(text.toUpperCase(), x, baseline);
        return g.getFontMetrics().stringWidth(text.toUpperCase());
    }

    static int keycapWidth(Graphics2D g, String key, float size) {
        return g.getFontMetrics(Theme.mono(size)).stringWidth(key) + Math.round(size * 0.9f);
    }

    /** A key name drawn as a keycap. Returns its width. */
    static int keycap(Graphics2D g, String key, int x, int baseline, float size) {
        Theme t = Theme.current();
        g.setFont(Theme.mono(size));
        FontMetrics fm = g.getFontMetrics();
        int width = keycapWidth(g, key, size);
        int top = baseline - fm.getAscent() - Math.round(size * 0.18f);
        int height = fm.getAscent() + fm.getDescent() + Math.round(size * 0.36f);
        int arc = Math.round(size * 0.5f);
        g.setColor(t.bg());
        g.fillRoundRect(x, top, width, height, arc, arc);
        g.setColor(t.sub());
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, top, width, height, arc, arc);
        g.setColor(t.text());
        g.drawString(key, x + (width - fm.stringWidth(key)) / 2, baseline);
        return width;
    }

    /**
     * A sentence in the interface font where {@code `backticked`} parts become keycaps.
     * Returns its width.
     */
    static int prose(Graphics2D g, String text, int x, int baseline, float size, Color color) {
        int at = x;
        String[] parts = text.split("`", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (i % 2 == 1) {
                at += keycap(g, parts[i], at + 2, baseline, size * 0.92f) + 4;
            } else {
                g.setFont(Theme.ui(size));
                g.setColor(color);
                g.drawString(parts[i], at, baseline);
                at += g.getFontMetrics().stringWidth(parts[i]);
            }
        }
        return at - x;
    }

    static int proseWidth(Graphics2D g, String text, float size) {
        int width = 0;
        String[] parts = text.split("`", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            width += i % 2 == 1 ? keycapWidth(g, parts[i], size * 0.92f) + 4
                    : g.getFontMetrics(Theme.ui(size)).stringWidth(parts[i]);
        }
        return width;
    }

    static void centered(Graphics2D g, String text, int width, int baseline) {
        g.drawString(text, (width - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }

    /**
     * Draws a Vim buffer: numbered lines, the cursor (a block, or a bar in insert mode), any
     * visual selection and search matches, and optionally a target cell to reach. Pass a
     * target row of -1 for none.
     */
    static void buffer(Graphics2D g, Vim vim, int textLeft, int y, float fontSize, int lineHeight,
                       int targetRow, int targetCol) {
        Theme t = Theme.current();
        java.util.List<String> lines = vim.lines();
        g.setFont(Theme.mono(fontSize));
        FontMetrics fm = g.getFontMetrics();
        int charW = fm.charWidth('m');
        String sought = vim.highlight();
        int[] preview = vim.searchPreview();
        for (int r = 0; r < lines.size(); r++) {
            String line = lines.get(r);
            int cellTop = y + r * lineHeight;
            int baseline = cellTop + fm.getAscent();
            // Every match of the search is lit; the one enter would jump to, more strongly.
            for (int at = sought.isEmpty() ? -1 : line.indexOf(sought); at >= 0;
                    at = line.indexOf(sought, at + 1)) {
                boolean next = preview != null && preview[0] == r && preview[1] == at;
                g.setColor(next ? t.wash(150) : new Color(t.sub().getRed(), t.sub().getGreen(),
                        t.sub().getBlue(), 135));
                g.fillRoundRect(textLeft + at * charW - 1, cellTop, sought.length() * charW + 2,
                        fm.getHeight(), 6, 6);
            }
            g.setFont(Theme.mono(13f));
            g.setColor(t.sub());
            String number = Integer.toString(r + 1);
            g.drawString(number, textLeft - charW - g.getFontMetrics().stringWidth(number),
                    baseline - 2);
            g.setFont(Theme.mono(fontSize));
            // One cell past the end, because visual mode can put the cursor there.
            for (int c = 0; c <= line.length(); c++) {
                int x = textLeft + c * charW;
                boolean cursor = r == vim.row() && c == vim.col()
                        && vim.mode() != Vim.Mode.INSERT;
                boolean target = r == targetRow && c == targetCol;
                if (vim.selected(r, c)) {
                    g.setColor(t.wash(70));
                    g.fillRect(x, cellTop, charW, fm.getHeight());
                }
                if (cursor) {
                    g.setColor(t.text());
                    g.fillRoundRect(x, cellTop, charW, fm.getHeight(), 4, 4);
                    g.setColor(t.panel());
                } else if (target) {
                    g.setColor(t.wash(45));
                    g.fillRoundRect(x - 2, cellTop - 1, charW + 4, fm.getHeight() + 2, 6, 6);
                    g.setColor(t.accent());
                    g.setStroke(new BasicStroke(2f));
                    g.drawRoundRect(x - 2, cellTop - 1, charW + 4, fm.getHeight() + 2, 6, 6);
                } else {
                    g.setColor(t.text());
                }
                if (c < line.length()) {
                    g.drawString(String.valueOf(line.charAt(c)), x, baseline);
                }
            }
            if (vim.mode() == Vim.Mode.INSERT && r == vim.row()) {
                g.setColor(t.accent());
                g.fillRect(textLeft + vim.col() * charW - 1, cellTop, 2, fm.getHeight());
            }
        }
    }

    /**
     * A key sequence in the notation the lessons use, such as {@code cwfox<esc>}, drawn as
     * keycaps: runs of ordinary keys together, named keys and spaces each on their own cap.
     * Returns its width.
     */
    static int sequence(Graphics2D g, String notation, int x, int baseline, float size) {
        int at = x;
        for (String chunk : chunks(notation)) {
            at += keycap(g, chunk, at, baseline, size) + 5;
        }
        return at - x;
    }

    static int sequenceWidth(Graphics2D g, String notation, float size) {
        int width = 0;
        for (String chunk : chunks(notation)) {
            width += keycapWidth(g, chunk, size) + 5;
        }
        return width;
    }

    static java.util.List<String> chunks(String notation) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        StringBuilder run = new StringBuilder();
        String[][] named = {{"<esc>", "esc"}, {"<enter>", "enter"}, {"<bs>", "bksp"},
                {"<c-r>", "ctrl-r"}, {" ", "space"}};
        for (int i = 0; i < notation.length(); ) {
            String name = null;
            for (String[] entry : named) {
                if (notation.startsWith(entry[0], i)) {
                    name = entry[1];
                    i += entry[0].length();
                    break;
                }
            }
            if (name == null) {
                run.append(notation.charAt(i++));
                continue;
            }
            if (!run.isEmpty()) {
                chunks.add(run.toString());
                run.setLength(0);
            }
            chunks.add(name);
        }
        if (!run.isEmpty()) {
            chunks.add(run.toString());
        }
        return chunks;
    }
}
