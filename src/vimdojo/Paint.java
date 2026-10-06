package vimdojo;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Drawing helpers shared by the screens. */
final class Paint {
    private Paint() {
    }

    /** Draws text in which {@code `backticked`} parts use the accent colour. Returns its width. */
    static int rich(Graphics2D g, String text, int x, int y, Color plain, Color accent) {
        FontMetrics fm = g.getFontMetrics();
        int at = x;
        String[] parts = text.split("`", -1);
        for (int i = 0; i < parts.length; i++) {
            g.setColor(i % 2 == 0 ? plain : accent);
            g.drawString(parts[i], at, y);
            at += fm.stringWidth(parts[i]);
        }
        return at - x;
    }

    static int richWidth(Graphics2D g, String text) {
        return g.getFontMetrics().stringWidth(text.replace("`", ""));
    }

    static void centered(Graphics2D g, String text, int width, int y) {
        g.drawString(text, (width - g.getFontMetrics().stringWidth(text)) / 2, y);
    }
}
