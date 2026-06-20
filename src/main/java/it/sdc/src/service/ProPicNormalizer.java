package it.sdc.src.service;

import it.sdc.src.config.ProPicEncodingProperties;
import it.sdc.src.exceptions.BadImageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * This class normalizes profile pictures in a standard format
 */
@Component
@RequiredArgsConstructor
public class ProPicNormalizer {
    private final ProPicEncodingProperties properties;

    /**
     * Converts any image to jpg and changes the resolution accordingly
     * @param inputImage raw input image
     * @return raw jpg bytes
     * @throws BadImageException if the image is in a bad format or not square
     * @throws IOException when memory IO fails
     */
    public byte[] normalizeImage(byte[] inputImage) throws IOException {
        try (
                ByteArrayInputStream bis = new ByteArrayInputStream(inputImage);
                ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ) {
            BufferedImage image = ImageIO.read(bis);
            if (image == null) throw new BadImageException("Bad image format");

            // Must be square. A cropping tool integrated in the frontend should take care of it
            if (image.getWidth() != image.getHeight())
                throw new BadImageException("Image not square");

            // Scale to standard resolution (either up or down)
            // also drops alpha channel and fills transparent areas with white
            image = scale(image);

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(properties.getCompressionRatio() / 100.0f);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
                writer.dispose();
            }

            return bos.toByteArray();
        }
    }

    /**
     * Scale the image to the standard resolution
     * @param img image to scale
     * @return the scaled image
     */
    private BufferedImage scale(BufferedImage img) {
        BufferedImage scaled = new BufferedImage(
                properties.getResolutionPx(),
                properties.getResolutionPx(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = scaled.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, properties.getResolutionPx(), properties.getResolutionPx());

        // BICUBIC for downscaling, BILINEAR for upscaling
        boolean isDownscale = img.getWidth() > properties.getResolutionPx();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                isDownscale
                        ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(img, 0, 0, properties.getResolutionPx(), properties.getResolutionPx(), null);
        g.dispose();
        return scaled;
    }
}
