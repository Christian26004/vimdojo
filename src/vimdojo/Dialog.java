package vimdojo;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JComponent;

/**
 * A box over the screen with one text field, for the settings that need typing: erasing
 * progress, which asks for the data folder to be typed out first as GitHub does before deleting
 * a repository, and moving the data folder. The app routes keys here while it is open; see
 * {@link App#globalKey}.
 */
final class Dialog extends JComponent {
    /**
     * What a dialog says and does. The shown line, if any, is printed above the field; the
     * field starts with the initial text. The button works once the text is ready; its action
     * returns why it couldn't go ahead, or null once it has.
     */
    record Spec(String mode, String title, String body, String prompt, String shown,
                String initial, String button, Predicate<String> ready,
                Function<String, String> action) {
    }

    private static final int WIDTH = 560;
    private static final int PAD = 28;

    private final App app;
    private final StringBuilder typed = new StringBuilder();
    private Spec spec;
    private int caret;
    private String error;
    // Where the box and its buttons were last drawn, for clicks.
    private Rectangle panel = new Rectangle();
    private Rectangle cancel = new Rectangle();
    private Rectangle confirm = new Rectangle();

    Dialog(App app) {
        this.app = app;
        setOpaque(false);
        setVisible(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (confirm.contains(e.getPoint())) {
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

    void open(Spec spec) {
        this.spec = spec;
        typed.setLength(0);
        typed.append(spec.initial());
        caret = typed.length();
        error = null;
        setVisible(true);
        app.refresh();
    }

    void close() {
        setVisible(false);
        app.refresh();
    }

    /** What the bottom bar calls this dialog. */
    String mode() {
        return spec == null ? "" : spec.mode();
    }

    String typed() {
        return typed.toString();
    }

    /** Runs the action if the text is ready, closing on success and showing why not otherwise. */
    void confirm() {
        if (!spec.ready().test(typed())) {
            return;
        }
        error = spec.action().apply(typed());
        if (error == null) {
            setVisible(false);
        }
        app.refresh();
    }

    // ---- editing the field ----

    void type(char c) {
        typed.insert(caret++, c);
        edited();
    }

    void backspace() {
        if (caret > 0) {
            typed.deleteCharAt(--caret);
            edited();
        }
    }

    void delete() {
        if (caret < typed.length()) {
            typed.deleteCharAt(caret);
            edited();
        }
    }

    void moveCaret(int to) {
        caret = Math.max(0, Math.min(to, typed.length()));
        repaint();
    }

    int caret() {
        return caret;
    }

    /** Pastes the clipboard's text, on one line. */
    void paste() {
        try {
            Object text = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            for (char c : text.toString().replaceAll("[\\r\\n]+", " ").strip().toCharArray()) {
                typed.insert(caret++, c);
            }
            edited();
        } catch (Exception e) {
            // Nothing usable on the clipboard: nothing to paste.
        }
    }

    private void edited() {
        error = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (spec == null) {
            return;
        }
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        g.setColor(DocsView.WASH);
        g.fillRect(0, 0, getWidth(), getHeight());

        int w = Math.min(WIDTH, getWidth() - 40);
        int inner = w - PAD * 2;
        List<String> body = Paint.wrap(g, spec.body(), inner, 15f);
        int h = PAD + 30 + body.size() * 23 + 24 + (spec.shown() == null ? 0 : 26) + 30 + 46
                + (error == null ? 0 : 24) + 28 + 40 + PAD - 8;
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
        g.drawString(spec.title(), left, baseline);
        baseline += 34;
        for (String line : body) {
            Paint.prose(g, line, left, baseline, 15f, t.text());
            baseline += 23;
        }
        baseline += 14;
        Paint.prose(g, spec.prompt(), left, baseline, 14f, t.sub());
        if (spec.shown() != null) {
            baseline += 26;
            g.setFont(Theme.mono(15f));
            g.setColor(t.text());
            g.drawString(spec.shown(), left, baseline);
        }

        // The field, outlined in the accent once its text is ready.
        boolean ready = spec.ready().test(typed());
        int fieldY = baseline + 16;
        g.setColor(t.panel());
        g.fillRoundRect(left, fieldY, inner, 38, 10, 10);
        g.setColor(ready ? t.accent() : t.sub());
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(left, fieldY, inner, 38, 10, 10);
        g.setFont(Theme.mono(15f));
        FontMetrics fm = g.getFontMetrics();
        // Long text scrolls so the caret stays in view.
        int from = 0;
        while (from < caret && fm.stringWidth(typed.substring(from, caret)) > inner - 26) {
            from++;
        }
        int to = typed.length();
        while (to > caret && fm.stringWidth(typed.substring(from, to)) > inner - 26) {
            to--;
        }
        g.setColor(t.text());
        g.drawString(typed.substring(from, to), left + 12, fieldY + 25);
        g.setColor(t.accent());
        g.fillRect(left + 12 + fm.stringWidth(typed.substring(from, caret)), fieldY + 9, 2, 20);

        int below = fieldY + 38;
        if (error != null) {
            below += 24;
            g.setFont(Theme.bold(13.5f));
            g.setColor(t.accent());
            g.drawString(error, left, below - 4);
        }

        // Buttons: cancel always, the action once the text is ready.
        int buttonY = below + 24;
        g.setFont(Theme.bold(14f));
        fm = g.getFontMetrics();
        int confirmW = fm.stringWidth(spec.button()) + 32;
        confirm = new Rectangle(x + w - PAD - confirmW, buttonY, confirmW, 36);
        String cancelLabel = "Cancel";
        int cancelW = fm.stringWidth(cancelLabel) + 32;
        cancel = new Rectangle(confirm.x - 12 - cancelW, buttonY, cancelW, 36);
        g.setColor(t.panel());
        g.fillRoundRect(cancel.x, cancel.y, cancel.width, cancel.height, 10, 10);
        g.setColor(t.text());
        g.drawString(cancelLabel, cancel.x + 16, cancel.y + 23);
        g.setColor(ready ? t.accent() : t.panel());
        g.fillRoundRect(confirm.x, confirm.y, confirm.width, confirm.height, 10, 10);
        g.setColor(ready ? t.bg() : t.sub());
        g.drawString(spec.button(), confirm.x + 16, confirm.y + 23);
        Paint.prose(g, "`esc` cancel", left, buttonY + 23, 12.5f, t.sub());
    }
}
