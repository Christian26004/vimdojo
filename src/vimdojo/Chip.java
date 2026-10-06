package vimdojo;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.JComponent;

/** Flat text button. Never takes keyboard focus, so clicking one doesn't interrupt typing. */
final class Chip extends JComponent {
    private final Supplier<String> text;
    private final float size;
    private final BooleanSupplier active;
    private boolean hover;

    Chip(Supplier<String> text, float size, BooleanSupplier active, Runnable onClick) {
        this.text = text;
        this.size = size;
        this.active = active;
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    onClick.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    Chip(String text, float size, BooleanSupplier active, Runnable onClick) {
        this(() -> text, size, active, onClick);
    }

    /** A disabled chip keeps its place in the layout but is greyed out and ignores clicks. */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Theme.font(size));
        return new Dimension(fm.stringWidth(text.get()) + 20, fm.getHeight() + 12);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        g.setFont(Theme.font(size));
        FontMetrics fm = g.getFontMetrics();
        if (!isEnabled()) {
            // Halfway between the dimmed text colour and the background.
            g.setColor(new Color((t.sub().getRed() + t.bg().getRed()) / 2,
                    (t.sub().getGreen() + t.bg().getGreen()) / 2,
                    (t.sub().getBlue() + t.bg().getBlue()) / 2));
        } else {
            g.setColor(active.getAsBoolean() ? t.main() : hover ? t.text() : t.sub());
        }
        g.drawString(text.get(), 10, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
    }
}
