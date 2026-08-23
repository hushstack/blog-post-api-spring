package com.cachewraith.blog_post_api_spring.integration.storage;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Filesystem-backed storage for local development.
 *
 * <p>The filename is generated, never taken from the upload — a client-supplied name is the classic
 * path-traversal vector. The declared content type is checked against an allowlist and then
 * confirmed by actually decoding the bytes as an image, so a renamed script cannot slip through
 * (OWASP A08, A03).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    @Override
    public String storeImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "File is empty");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !properties.getAllowedContentTypes().contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Unsupported content type");
        }

        String extension = extensionFor(contentType);
        String storedName = UUID.randomUUID() + "." + extension;

        try {
            // Decoding proves the bytes really are an image, rather than trusting the header.
            try (InputStream probe = file.getInputStream()) {
                if (ImageIO.read(probe) == null) {
                    throw new BusinessException(ErrorCode.INVALID_IMAGE, "File is not a valid image");
                }
            }

            Path root = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
            Path target = root.resolve(folder).resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE, "Invalid target path");
            }

            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return properties.getPublicBaseUrl() + "/" + folder + "/" + storedName;

        } catch (IOException ex) {
            log.error("Failed to store upload in folder {}", folder, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not store file");
        }
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(properties.getPublicBaseUrl())) {
            return;
        }
        String relative = url.substring(properties.getPublicBaseUrl().length());
        Path root = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
        Path target = root.resolve(relative.startsWith("/") ? relative.substring(1) : relative)
                .normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Could not delete stored file for url {}", url, ex);
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new BusinessException(ErrorCode.INVALID_IMAGE, "Unsupported content type");
        };
    }
}
