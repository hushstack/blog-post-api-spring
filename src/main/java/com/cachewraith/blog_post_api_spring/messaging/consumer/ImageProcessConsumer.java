package com.cachewraith.blog_post_api_spring.messaging.consumer;

import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.integration.storage.StorageProperties;
import com.cachewraith.blog_post_api_spring.integration.storage.StorageService;
import com.cachewraith.blog_post_api_spring.messaging.event.ImageProcessEvent;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Downscales oversized uploads in place.
 *
 * <p>The upload path already stores the originals and their {@code PostImage} rows synchronously,
 * so a post is never left without its images if this consumer is down; this stage only shrinks what
 * is already there.
 *
 * <p>Rewriting the object at its existing URL, rather than writing a second derivative file, means
 * no schema column and no row update: every {@code PostImage} that referenced the image still does.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageProcessConsumer {

    private final StorageService storageService;
    private final StorageProperties properties;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_IMAGE_PROCESS)
    public void handle(ImageProcessEvent event) {
        log.info("Processing {} image(s) for post {}", event.urls().size(), event.postId());
        for (String url : event.urls()) {
            try {
                optimise(url);
            } catch (RuntimeException ex) {
                // One bad image must not nack the batch and redeliver the good ones; the original
                // stays in place and readable, which is the safe outcome here (OWASP A10).
                log.warn("Skipped image {} for post {}", url, event.postId(), ex);
            }
        }
    }

    private void optimise(String url) {
        String format = writableFormatFor(url);
        if (format == null) {
            // GIF may be animated and WebP has no ImageIO writer in the JDK; re-encoding either
            // would silently flatten or corrupt it, so both are left exactly as uploaded.
            log.debug("No safe re-encoder for {}; left as uploaded", url);
            return;
        }

        Optional<byte[]> stored = storageService.read(url);
        if (stored.isEmpty()) {
            log.debug("Nothing stored at {}; nothing to optimise", url);
            return;
        }

        BufferedImage source = decode(stored.get(), url);
        if (source == null) {
            return;
        }

        int max = properties.getMaxDimensionPixels();
        int longestEdge = Math.max(source.getWidth(), source.getHeight());
        if (longestEdge <= max) {
            log.debug("Image {} is {}px on its longest edge; already within {}px", url, longestEdge, max);
            return;
        }

        double scale = (double) max / longestEdge;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        byte[] encoded = encode(resize(source, width, height, format), format, url);
        if (encoded == null) {
            return;
        }

        storageService.replace(url, encoded);
        log.info(
                "Optimised {} from {}x{} to {}x{} ({} -> {} bytes)",
                url,
                source.getWidth(),
                source.getHeight(),
                width,
                height,
                stored.get().length,
                encoded.length);
    }

    private BufferedImage resize(BufferedImage source, int width, int height, String format) {
        // JPEG cannot carry an alpha channel, so a transparent source must be flattened to RGB
        // before encoding or the writer produces an unreadable file.
        int type = "jpg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage target = new BufferedImage(width, height, type);

        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private BufferedImage decode(byte[] bytes, String url) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                log.warn("Could not decode stored image {}", url);
            }
            return image;
        } catch (IOException ex) {
            log.warn("Could not decode stored image {}", url, ex);
            return null;
        }
    }

    private byte[] encode(BufferedImage image, String format, String url) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                log.warn("No ImageIO writer for format {} of {}", format, url);
                return null;
            }
            return out.toByteArray();
        } catch (IOException ex) {
            log.warn("Could not encode image {}", url, ex);
            return null;
        }
    }

    /** The ImageIO format name to re-encode with, or null when the format must not be rewritten. */
    private String writableFormatFor(String url) {
        String path = url.toLowerCase(Locale.ROOT);
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return switch (path.substring(dot + 1)) {
            case "jpg", "jpeg" -> "jpg";
            case "png" -> "png";
            default -> null;
        };
    }
}
