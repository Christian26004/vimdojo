package vimdojo;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/** The seal and name in the top corner. Clicking it returns to the current lesson. */
final class Brand extends JComponent {
    Brand(Runnable onClick) {
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(170, 34));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onClick.run();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        g.setColor(t.accent());
        g.fillRoundRect(0, 3, 28, 28, 8, 8);
        g.setFont(Theme.mono(15f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(t.bg());
        g.drawString("vi", (28 - fm.stringWidth("vi")) / 2, 3 + (28 - fm.getHeight()) / 2
                + fm.getAscent());
        g.setFont(Theme.bold(16f));
        g.setColor(t.text());
        g.drawString("vimdojo", 40, 23);
    }
}
