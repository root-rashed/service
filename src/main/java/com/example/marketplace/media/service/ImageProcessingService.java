package com.example.marketplace.media.service;

import com.example.marketplace.common.exception.ValidationException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates and sanitizes uploaded images before they reach storage.
 *
 * Checks: size, extension whitelist, declared MIME type, real file signature (magic bytes),
 * extension/content agreement, and for JPG/PNG a full decode with dimension limits (protects against
 * decompression bombs). JPG/PNG are then re-encoded (and downscaled), which removes metadata and any
 * hidden payload. WEBP has no decoder in the JDK, so it is accepted only after a strict RIFF/WEBP
 * header check and stored unchanged.
 */
@Service
public class ImageProcessingService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 12_000;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String UNSUPPORTED = "Only JPG, PNG and WEBP images are allowed.";

    /** Sanitized image ready to store. */
    public record ProcessedImage(byte[] content, String extension) {
    }

    private enum ImageType {
        JPEG("jpg", "jpeg"), PNG("png"), WEBP("webp");

        private final Set<String> extensions;

        ImageType(String... extensions) {
            this.extensions = Set.of(extensions);
        }

        boolean matches(String extension) {
            return extensions.contains(extension);
        }
    }

    public ProcessedImage process(MultipartFile file, int maxEdgePx) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Please choose an image.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException("Each image must be 5 MB or smaller.");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ValidationException(UNSUPPORTED);
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException(UNSUPPORTED);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ValidationException("Could not read the uploaded file.");
        }

        ImageType type = detectType(bytes);
        if (type == null || !type.matches(extension)) {
            throw new ValidationException("This file is not a valid JPG, PNG or WEBP image.");
        }
        if (type == ImageType.WEBP) {
            return new ProcessedImage(bytes, "webp");
        }
        return reencode(bytes, type, maxEdgePx);
    }

    // ---------- Detection ----------

    private ImageType detectType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return ImageType.PNG;
        }
        if (b.length >= 16
                && ascii(b, 0, 4).equals("RIFF")
                && ascii(b, 8, 4).equals("WEBP")
                && Set.of("VP8 ", "VP8L", "VP8X").contains(ascii(b, 12, 4))) {
            return ImageType.WEBP;
        }
        return null;
    }

    private String ascii(byte[] b, int offset, int length) {
        return new String(b, offset, length, StandardCharsets.US_ASCII);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String ext = StringUtils.getFilenameExtension(StringUtils.cleanPath(filename));
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    // ---------- Decode, resize, re-encode ----------

    private ProcessedImage reencode(byte[] bytes, ImageType type, int maxEdgePx) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ValidationException("This file is not a valid image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new ValidationException("This image's dimensions are too large.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new ValidationException("This file is not a valid image.");
                }

                boolean keepAlpha = type == ImageType.PNG && image.getColorModel().hasAlpha();
                int longest = Math.max(width, height);
                if (longest > maxEdgePx) {
                    double scale = (double) maxEdgePx / longest;
                    image = resize(image,
                            Math.max(1, (int) Math.round(width * scale)),
                            Math.max(1, (int) Math.round(height * scale)),
                            keepAlpha);
                } else if (type == ImageType.JPEG || !keepAlpha) {
                    image = resize(image, width, height, false); // normalize to plain RGB
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                String format = type == ImageType.JPEG ? "jpg" : "png";
                if (!ImageIO.write(image, format, out)) {
                    throw new ValidationException("This image format is not supported.");
                }
                return new ProcessedImage(out.toByteArray(), format);
            } finally {
                reader.dispose();
            }
        } catch (ValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ValidationException("This file is not a valid image.");
        }
    }

    private BufferedImage resize(BufferedImage source, int width, int height, boolean alpha) {
        BufferedImage target = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            if (!alpha) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }
}
