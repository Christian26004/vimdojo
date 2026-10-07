package vimdojo;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Colors and type. The look is ink on paper with a single vermilion accent, like a seal on a
 * page, plus one green kept for marking something done better than expected: a quiet sans-serif for the interface, and monospace only where it is Vim's text or keys.
 */
public record Theme(String name, Color bg, Color panel, Color accent, Color sub, Color text,
                    Color good) {

    public static final Theme[] ALL = {
        // The first theme is the default.
        of("ink", 0x1b1a19, 0x272523, 0xe2583e, 0x7d766b, 0xe8e1d3, 0x6fbf8a),
        of("paper", 0xf4efe6, 0xe9e2d3, 0xc8402f, 0x9a917f, 0x2b2722, 0x3d8f5c),
        of("moss", 0x1e2923, 0x28362e, 0xe0b04a, 0x7f9186, 0xe6eadf, 0x8fdba6),
        of("indigo", 0x1c2132, 0x262c42, 0xf08a5d, 0x7c85a3, 0xe4e7f2, 0x7fd1a0),
    };

    private static final String MONO = pick(Font.MONOSPACED, "JetBrains Mono", "SF Mono", "Menlo",
            "Cascadia Mono", "Consolas", "DejaVu Sans Mono");
    private static final String SANS = pick(Font.SANS_SERIF, "Avenir Next", "Segoe UI",
            "Helvetica Neue", "Inter", "Noto Sans");
    private static Theme current = ALL[0];

    private static Theme of(String name, int bg, int panel, int accent, int sub, int text,
                            int good) {
        return new Theme(name, new Color(bg), new Color(panel), new Color(accent), new Color(sub),
                new Color(text), new Color(good));
    }

    public static Theme current() {
        return current;
    }

    public static void select(String name) {
        for (Theme t : ALL) {
            if (t.name.equals(name)) {
                current = t;
            }
        }
    }

    public static Theme next() {
        int i = Arrays.asList(ALL).indexOf(current);
        current = ALL[(i + 1) % ALL.length];
        return current;
    }

    /** The accent at reduced strength, for washes behind text. */
    public Color wash(int alpha) {
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha);
    }

    /** Monospace, for the buffer and for key names. */
    public static Font mono(float size) {
        return new Font(MONO, Font.PLAIN, 1).deriveFont(size);
    }

    /** The interface typeface. */
    public static Font ui(float size) {
        return new Font(SANS, Font.PLAIN, 1).deriveFont(size);
    }

    public static Font bold(float size) {
        return new Font(SANS, Font.BOLD, 1).deriveFont(size);
    }

    /** Spaced-out type for small labels; the caller supplies upper-case text. */
    public static Font caps(float size) {
        return bold(size).deriveFont(Map.of(TextAttribute.TRACKING, 0.14f));
    }

    /** Turns on antialiasing; every custom-painted component starts with this. */
    public static Graphics2D prep(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    private static String pick(String fallback, String... wanted) {
        List<String> installed = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String family : wanted) {
            if (installed.contains(family)) {
                return family;
            }
        }
        return fallback;
    }
}
