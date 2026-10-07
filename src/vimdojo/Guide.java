package vimdojo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The tour shown on first launch. Everything is blurred except the part being described, with a
 * sentence about it alongside. Enter or a click moves on, and escape ends the tour early.
 */
final class Guide extends JComponent {
    /**
     * One stop of the tour: what to bring on screen, the parts to leave sharp (in this
     * component's coordinates, margins included), and what to say about them.
     */
    record Step(Runnable show, Supplier<List<Rectangle>> spotlight, String text) {
    }

    // The blur works on a copy this many times smaller, which is quick and smooths it out.
    private static final int SHRINK = 4;
    private static final int RADIUS = 3;
    private static final int GAP = 14;
    private static final int BUBBLE_WIDTH = 380;
    private static final int BUBBLE_PAD = 22;
    private static final int LINE = 24;
    private static final float SIZE = 15f;
    private static final Color DIM = new Color(0, 0, 0, 80);

    private final JComponent behind;
    private final List<Step> steps;
    private final Runnable finished;
    private final Runnable skipped;
    private int index;
    // The screen as it stood when this step began, blurred. Made again on each step and resize.
    private BufferedImage blurred;

    /**
     * @param behind   what the tour lies over, and is blurred
     * @param finished run after the last step
     * @param skipped  run when the tour is ended early
     */
    Guide(JComponent behind, List<Step> steps, Runnable finished, Runnable skipped) {
        this.behind = behind;
        this.steps = steps;
        this.finished = finished;
        this.skipped = skipped;
        setOpaque(false);
        setVisible(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    next();
                }
            }
        });
        // Swallows the wheel, which would otherwise scroll the screen underneath.
        addMouseWheelListener(e -> {
        });
    }

    boolean active() {
        return isVisible();
    }

    /** Which step is showing, counting from zero. */
    int step() {
        return index;
    }

    int steps() {
        return steps.size();
    }

    void start() {
        setVisible(true);
        enter(0);
    }

    void next() {
        if (index == steps.size() - 1) {
            setVisible(false);
            finished.run();
        } else {
            enter(index + 1);
        }
    }

    void skip() {
        setVisible(false);
        skipped.run();
    }

    private void enter(int step) {
        index = step;
        steps.get(step).show().run();
        blurred = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.prep(g0);
        Theme t = Theme.current();
        int w = getWidth();
        int h = getHeight();
        if (blurred == null || blurred.getWidth() != Math.max(1, w / SHRINK)
                || blurred.getHeight() != Math.max(1, h / SHRINK)) {
            blurred = blur(w, h);
        }

        List<RoundRectangle2D> spots = new ArrayList<>();
        Rectangle around = null;
        for (Rectangle r : steps.get(index).spotlight().get()) {
            // Kept a pixel inside the window, so the whole outline shows.
            Rectangle spot = r.intersection(new Rectangle(1, 1, w - 2, h - 2));
            if (spot.isEmpty()) {
                continue;
            }
            spots.add(new RoundRectangle2D.Double(spot.x, spot.y, spot.width, spot.height,
                    16, 16));
            around = around == null ? spot : around.union(spot);
        }

        // Everything but the spotlit parts: the blurred copy, dimmed a little.
        Area veil = new Area(new Rectangle(0, 0, w, h));
        for (RoundRectangle2D spot : spots) {
            veil.subtract(new Area(spot));
        }
        Graphics2D v = (Graphics2D) g.create();
        v.clip(veil);
        v.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        v.drawImage(blurred, 0, 0, w, h, null);
        v.setColor(DIM);
        v.fill(veil);
        v.dispose();

        g.setColor(t.accent());
        g.setStroke(new BasicStroke(1.5f));
        for (RoundRectangle2D spot : spots) {
            g.draw(spot);
        }
        paintBubble(g, around, w, h);
    }

    /** The sentence for this step, beside the spotlit parts if there is room, else over them. */
    private void paintBubble(Graphics2D g, Rectangle around, int w, int h) {
        Theme t = Theme.current();
        boolean last = index == steps.size() - 1;
        List<String> lines = Paint.wrap(g, steps.get(index).text(), BUBBLE_WIDTH - 2 * BUBBLE_PAD,
                SIZE);
        String hint = last ? "`enter` or click to start your first lesson"
                : "`enter` or click to continue   `esc` skip";
        int bw = Math.min(BUBBLE_WIDTH, w - 32);
        int bh = BUBBLE_PAD + 14 + 14 + lines.size() * LINE + 18 + 16 + BUBBLE_PAD - 6;

        int x;
        int y;
        if (around == null) {
            x = (w - bw) / 2;
            y = (h - bh) / 2;
        } else {
            // Below, above, left, right, and failing all those inside, near the bottom.
            x = around.x + (around.width - bw) / 2;
            y = around.y + (around.height - bh) / 2;
            if (around.y + around.height + GAP + bh <= h - 12) {
                y = around.y + around.height + GAP;
            } else if (around.y - GAP - bh >= 12) {
                y = around.y - GAP - bh;
            } else if (around.x - GAP - bw >= 16) {
                x = around.x - GAP - bw;
            } else if (around.x + around.width + GAP + bw <= w - 16) {
                x = around.x + around.width + GAP;
            } else {
                y = around.y + around.height - bh - 28;
            }
        }
        x = Math.max(16, Math.min(x, w - bw - 16));
        y = Math.max(12, Math.min(y, h - bh - 12));

        g.setColor(t.panel());
        g.fillRoundRect(x, y, bw, bh, 16, 16);
        g.setColor(t.sub());
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, bw, bh, 16, 16);

        int baseline = y + BUBBLE_PAD + 10;
        Paint.label(g, "tour  " + (index + 1) + " of " + steps.size(), x + BUBBLE_PAD, baseline);
        baseline += 14 + LINE;
        for (String line : lines) {
            Paint.prose(g, line, x + BUBBLE_PAD, baseline, SIZE, t.text());
            baseline += LINE;
        }
        Paint.prose(g, hint, x + BUBBLE_PAD, baseline + 14, 12.5f, t.sub());
    }

    /** A small blurred copy of what lies behind the tour. */
    private BufferedImage blur(int w, int h) {
        BufferedImage full = new BufferedImage(Math.max(1, w), Math.max(1, h),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D fg = full.createGraphics();
        behind.paint(fg);
        fg.dispose();

        int sw = Math.max(1, w / SHRINK);
        int sh = Math.max(1, h / SHRINK);
        BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = small.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        sg.drawImage(full, 0, 0, sw, sh, null);
        sg.dispose();

        int[] pixels = small.getRGB(0, 0, sw, sh, null, 0, sw);
        // Two rounds of a box blur each way come close to a gaussian.
        for (int round = 0; round < 2; round++) {
            pixels = boxBlur(pixels, sw, sh, true);
            pixels = boxBlur(pixels, sw, sh, false);
        }
        small.setRGB(0, 0, sw, sh, pixels, 0, sw);
        return small;
    }

    /** Averages each pixel with its neighbours along rows or columns, repeating the edges. */
    private static int[] boxBlur(int[] in, int w, int h, boolean rows) {
        int[] out = new int[in.length];
        int length = rows ? w : h;
        int lines = rows ? h : w;
        int span = 2 * RADIUS + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int r = 0;
                int gr = 0;
                int b = 0;
                for (int k = -RADIUS; k <= RADIUS; k++) {
                    int at = Math.max(0, Math.min(length - 1, i + k));
                    int p = rows ? in[line * w + at] : in[at * w + line];
                    r += (p >> 16) & 0xff;
                    gr += (p >> 8) & 0xff;
                    b += p & 0xff;
                }
                int to = rows ? line * w + i : i * w + line;
                out[to] = (r / span) << 16 | (gr / span) << 8 | b / span;
            }
        }
        return out;
    }
}
