package vimdojo;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.List;

/** Colour palette in the Monkeytype sense: background, accent, dimmed text, typed text, errors. */
public record Theme(String name, Color bg, Color subAlt, Color main, Color sub, Color text,
                    Color error, Color errorExtra) {

    public static final Theme[] ALL = {
        of("serika dark", 0x323437, 0x2c2e31, 0xe2b714, 0x646669, 0xd1d0c5, 0xca4754, 0x7e2a33),
        of("nord", 0x242933, 0x2e3440, 0x88c0d0, 0x617b94, 0xd8dee9, 0xbf616a, 0x793e44),
        of("dracula", 0x282a36, 0x21222c, 0xbd93f9, 0x6272a4, 0xf8f8f2, 0xff5555, 0xa63232),
        of("carbon", 0x313131, 0x2b2b2b, 0xf66e0d, 0x616161, 0xf5e6c8, 0xe72d2d, 0x7e2a33),
        of("paper", 0xeeeeee, 0xdddddd, 0x444444, 0xb2b2b2, 0x444444, 0xd70000, 0xd75f5f),
    };

    private static final String FAMILY = pickFamily();
    private static Theme current = ALL[0];

    private static Theme of(String name, int bg, int subAlt, int main, int sub, int text,
                            int error, int errorExtra) {
        return new Theme(name, new Color(bg), new Color(subAlt), new Color(main), new Color(sub),
                new Color(text), new Color(error), new Color(errorExtra));
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

    public static Font font(float size) {
        return new Font(FAMILY, Font.PLAIN, 1).deriveFont(size);
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

    private static String pickFamily() {
        List<String> installed = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String f : new String[] {"Roboto Mono", "JetBrains Mono", "SF Mono", "Menlo",
                "Cascadia Mono", "Consolas", "DejaVu Sans Mono"}) {
            if (installed.contains(f)) {
                return f;
            }
        }
        return Font.MONOSPACED;
    }
}
