package vimdojo;

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

/** A text link in the status bar. Never takes keyboard focus, so it can't steal keys from Vim. */
final class Chip extends JComponent {
    private static final float SIZE = 12.5f;

    private final Supplier<String> text;
    private final BooleanSupplier active;
    private boolean hover;

    Chip(Supplier<String> text, BooleanSupplier active, Runnable onClick) {
        this.text = text;
        this.active = active;
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onClick.run();
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

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Theme.ui(SIZE));
        return new Dimension(fm.stringWidth(text.get()) + 26, StatusBar.HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        g.setFont(Theme.ui(SIZE));
        FontMetrics fm = g.getFontMetrics();
        int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(active.getAsBoolean() || hover ? t.text() : t.sub());
        g.drawString(text.get(), 13, baseline);
        if (active.getAsBoolean()) {
            g.setColor(t.accent());
            g.fillRect(13, getHeight() - 3, fm.stringWidth(text.get()), 3);
        }
    }
}
