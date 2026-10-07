package vimdojo;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;

/**
 * Asks before erasing every result, the way GitHub asks before deleting a repository: the erase
 * only goes ahead once the location of the data folder has been typed out in full. The app
 * routes keys here while it is open; see {@link App#globalKey}.
 */
final class EraseDialog extends JComponent {
    private static final int WIDTH = 560;
    private static final int PAD = 28;

    private final App app;
    private final StringBuilder typed = new StringBuilder();
    // Where the buttons were last drawn, for clicks.
    private Rectangle panel = new Rectangle();
    private Rectangle cancel = new Rectangle();
    private Rectangle erase = new Rectangle();

    EraseDialog(App app) {
        this.app = app;
        setOpaque(false);
        setVisible(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (erase.contains(e.getPoint())) {
                    confirm();
                } else if (cancel.contains(e.getPoint()) || !panel.contains(e.getPoint())) {
                    close();
                }
            }
        });
        // Swallows the wheel, which would otherwise scroll the screen underneath.
        addMouseWheelListener(e -> {
        });
    }

    /**
     * The data folder as it is shown and to be typed: under the home folder as ~/..., except on
     * Windows, where the full path is what people recognise.
     */
    static String folder() {
        Path dir = Settings.dataDir().toAbsolutePath().normalize();
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (File.separatorChar == '/' && dir.startsWith(home) && !dir.equals(home)) {
            return "~/" + home.relativize(dir);
        }
        return dir.toString();
    }

    /** Whether what has been typed names the data folder, as shown or in full. */
    boolean matches() {
        String text = typed.toString().strip();
        return text.equals(folder())
                || text.equals(Settings.dataDir().toAbsolutePath().normalize().toString());
    }

    void open() {
        typed.setLength(0);
        setVisible(true);
        app.refresh();
    }

    void close() {
        setVisible(false);
        app.refresh();
    }

    void type(char c) {
        typed.append(c);
        repaint();
    }

    void backspace() {
        if (!typed.isEmpty()) {
            typed.setLength(typed.length() - 1);
            repaint();
        }
    }

    /** Erases, if the location has been typed; otherwise nothing happens. */
    void confirm() {
        if (matches()) {
            setVisible(false);
            app.eraseProgress();
        }
    }

    String typed() {
        return typed.toString();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        g.setColor(DocsView.WASH);
        g.fillRect(0, 0, getWidth(), getHeight());

        int w = Math.min(WIDTH, getWidth() - 40);
        int inner = w - PAD * 2;
        List<String> warning = Paint.wrap(g, "Erasing data will revert all lesson progression. "
                + "This can't be undone.", inner, 15f);
        int h = PAD + 30 + warning.size() * 23 + 24 + 22 + 30 + 46 + 28 + 40 + PAD - 8;
        int x = (getWidth() - w) / 2;
        int y = Math.max(12, (getHeight() - h) / 2);
        panel = new Rectangle(x, y, w, h);
        g.setColor(t.bg());
        g.fillRoundRect(x, y, w, h, 18, 18);
        g.setColor(t.accent());
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, 18, 18);

        int left = x + PAD;
        int baseline = y + PAD + 18;
        g.setFont(Theme.bold(20f));
        g.setColor(t.text());
        g.drawString("Erase all progress?", left, baseline);
        baseline += 34;
        for (String line : warning) {
            Paint.prose(g, line, left, baseline, 15f, t.text());
            baseline += 23;
        }
        baseline += 14;
        Paint.prose(g, "To confirm, type the location of your vimdojo data:", left, baseline, 14f,
                t.sub());
        baseline += 26;
        g.setFont(Theme.mono(15f));
        g.setColor(t.text());
        g.drawString(folder(), left, baseline);

        // The field, outlined in the accent once it matches.
        int fieldY = baseline + 16;
        g.setColor(t.panel());
        g.fillRoundRect(left, fieldY, inner, 38, 10, 10);
        g.setColor(matches() ? t.accent() : t.sub());
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(left, fieldY, inner, 38, 10, 10);
        g.setFont(Theme.mono(15f));
        FontMetrics fm = g.getFontMetrics();
        String text = typed.toString();
        // Long input scrolls so its end, where the caret is, stays in view.
        while (fm.stringWidth(text) > inner - 26 && !text.isEmpty()) {
            text = text.substring(1);
        }
        g.setColor(t.text());
        g.drawString(text, left + 12, fieldY + 25);
        g.setColor(t.accent());
        g.fillRect(left + 12 + fm.stringWidth(text) + 1, fieldY + 9, 2, 20);

        // Buttons: cancel always, erase only once the location matches.
        int buttonY = fieldY + 38 + 24;
        g.setFont(Theme.bold(14f));
        fm = g.getFontMetrics();
        String eraseLabel = "Erase progress";
        int eraseW = fm.stringWidth(eraseLabel) + 32;
        erase = new Rectangle(x + w - PAD - eraseW, buttonY, eraseW, 36);
        String cancelLabel = "Cancel";
        int cancelW = fm.stringWidth(cancelLabel) + 32;
        cancel = new Rectangle(erase.x - 12 - cancelW, buttonY, cancelW, 36);
        g.setColor(t.panel());
        g.fillRoundRect(cancel.x, cancel.y, cancel.width, cancel.height, 10, 10);
        g.setColor(t.text());
        g.drawString(cancelLabel, cancel.x + 16, cancel.y + 23);
        boolean ready = matches();
        g.setColor(ready ? t.accent() : t.panel());
        g.fillRoundRect(erase.x, erase.y, erase.width, erase.height, 10, 10);
        g.setColor(ready ? t.bg() : t.sub());
        g.drawString(eraseLabel, erase.x + 16, erase.y + 23);
        Paint.prose(g, "`esc` cancel", left, buttonY + 23, 12.5f, t.sub());
    }
}
