package com.cachewraith.blog_post_api_spring.integration.storage;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Filesystem-backed storage for local development.
 *
 * <p>Validation of the upload itself lives in {@link AbstractStorageService}; what is left here is
 * only the disk mechanics. Every path is resolved under the storage root and re-checked with {@code
 * startsWith} after normalising, which is what stops a crafted URL containing {@code ../} from
 * reaching outside it (OWASP A01).
 *
 * <p>Selected by {@code app.storage.driver=local}; see {@link StorageConfig}.
 */
@Slf4j
public class LocalStorageService extends AbstractStorageService {

    public LocalStorageService(StorageProperties properties) {
        super(properties);
    }

    @Override
    protected String publicBaseUrl() {
        return properties.getPublicBaseUrl();
    }

    @Override
    protected void write(String key, byte[] content, String contentType) {
        Path target =
                resolve(key)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.INVALID_IMAGE, "Invalid target path"));
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            log.error("Failed to store upload at {}", key, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not store file");
        }
    }

    @Override
    public Optional<byte[]> read(String url) {
        Path target = keyFor(url).flatMap(this::resolve).orElse(null);
        if (target == null || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException ex) {
            log.warn("Could not read stored file for url {}", url, ex);
            return Optional.empty();
        }
    }

    @Override
    public void replace(String url, byte[] content) {
        Path target =
                keyFor(url)
                        .flatMap(this::resolve)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.INVALID_IMAGE, "Unknown storage url"));
        if (!Files.isRegularFile(target)) {
            // Nothing to replace: the object was deleted between upload and processing.
            return;
        }
        try {
            Files.write(target, content);
        } catch (IOException ex) {
            log.error("Failed to replace stored file for url {}", url, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not replace file");
        }
    }

    @Override
    public void delete(String url) {
        Path target = keyFor(url).flatMap(this::resolve).orElse(null);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Could not delete stored file for url {}", url, ex);
        }
    }

    /** Object key to a path under the storage root, or empty if it would escape the root. */
    private Optional<Path> resolve(String key) {
        Path root = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        return target.startsWith(root) ? Optional.of(target) : Optional.empty();
    }
}
