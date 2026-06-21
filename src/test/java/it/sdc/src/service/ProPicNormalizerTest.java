package it.sdc.src.service;

import it.sdc.src.config.ProPicEncodingProperties;
import it.sdc.src.exceptions.BadImageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProPicNormalizerTest {
    private ProPicEncodingProperties properties;
    private ProPicNormalizer normalizer;

    @BeforeEach
    void setUp() {
        ProPicEncodingProperties properties = new ProPicEncodingProperties();
        properties.setResolutionPx(128);
        properties.setCompressionRatio(85);

        normalizer = new ProPicNormalizer(properties);
    }

    @Test
    void normalizeImage_shouldReturnJpegWithConfiguredResolution_whenImageIsValidAndSquare()
            throws IOException {

        byte[] input = createPngImage(256, 256, Color.RED);

        byte[] output = normalizer.normalizeImage(input);

        assertThat(output).isNotEmpty();

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));

        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(128);
        assertThat(result.getHeight()).isEqualTo(128);
    }

    @Test
    void normalizeImage_shouldThrowBadImageException_whenInputIsNotAnImage() {
        byte[] input = "not an image".getBytes();

        assertThatThrownBy(() -> normalizer.normalizeImage(input))
                .isInstanceOf(BadImageException.class)
                .hasMessage("Bad image format");
    }

    @Test
    void normalizeImage_shouldThrowBadImageException_whenImageIsNotSquare()
            throws IOException {

        byte[] input = createPngImage(300, 200, Color.BLUE);

        assertThatThrownBy(() -> normalizer.normalizeImage(input))
                .isInstanceOf(BadImageException.class)
                .hasMessage("Image not square");
    }

    @Test
    void normalizeImage_shouldScaleUp_whenImageIsSmallerThanConfiguredResolution()
            throws IOException {

        byte[] input = createPngImage(64, 64, Color.GREEN);

        byte[] output = normalizer.normalizeImage(input);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));

        assertThat(result.getWidth()).isEqualTo(128);
        assertThat(result.getHeight()).isEqualTo(128);
    }

    @Test
    void normalizeImage_shouldScaleDown_whenImageIsLargerThanConfiguredResolution()
            throws IOException {

        byte[] input = createPngImage(512, 512, Color.YELLOW);

        byte[] output = normalizer.normalizeImage(input);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));

        assertThat(result.getWidth()).isEqualTo(128);
        assertThat(result.getHeight()).isEqualTo(128);
    }

    @Test
    void normalizeImage_shouldDropAlphaChannelAndProduceRgbImage()
            throws IOException {

        byte[] input = createTransparentPngImage(256, 256);

        byte[] output = normalizer.normalizeImage(input);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));

        assertThat(result.getType()).isIn(
                BufferedImage.TYPE_INT_RGB,
                BufferedImage.TYPE_3BYTE_BGR
        );
        assertThat(result.getColorModel().hasAlpha()).isFalse();
    }

    private byte[] createPngImage(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();

        return toBytes(image, "png");
    }

    private byte[] createTransparentPngImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, width, height);
        g.dispose();

        return toBytes(image, "png");
    }

    private byte[] toBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }
}

