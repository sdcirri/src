package it.sdc.src.test.fixtures;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class GraphicsFixtures {

    private static byte[] toBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }

    public static byte[] createPngImage(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();

        return toBytes(image, "png");
    }

    public static byte[] createTransparentPngImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, width, height);
        g.dispose();

        return toBytes(image, "png");
    }
}
