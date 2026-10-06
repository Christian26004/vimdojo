import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;

/** Draws the app icon at the given size, as .png or .ico: java tools/Icon.java 1024 icon.png */
public final class Icon {
    public static void main(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // macOS icons sit on a rounded square inset from the canvas edge.
        int pad = size / 10;
        int box = size - 2 * pad;
        g.setColor(new Color(0x323437));
        g.fillRoundRect(pad, pad, box, box, box * 45 / 100, box * 45 / 100);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, box * 48 / 100));
        int textWidth = g.getFontMetrics().stringWidth("vi");
        int caret = box * 4 / 100;
        int x = (size - textWidth - caret * 2) / 2;
        int baseline = pad + box * 64 / 100;
        g.setColor(new Color(0xd1d0c5));
        g.drawString("vi", x, baseline);
        g.setColor(new Color(0xe2b714));
        g.fillRoundRect(x + textWidth + caret, baseline - box * 36 / 100, caret, box * 42 / 100,
                caret, caret);
        g.dispose();
        if (args[1].endsWith(".ico")) {
            writeIco(image, new File(args[1]));
        } else {
            ImageIO.write(image, "png", new File(args[1]));
        }
    }

    /** A Windows .ico file can simply wrap one PNG image, up to 256 pixels square. */
    private static void writeIco(BufferedImage image, File file) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        ByteBuffer header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) 1);
        // A width or height byte of 0 means 256.
        header.put((byte) (image.getWidth() % 256)).put((byte) (image.getHeight() % 256));
        header.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
        header.putInt(png.size()).putInt(22);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(header.array());
            png.writeTo(out);
        }
    }
}
