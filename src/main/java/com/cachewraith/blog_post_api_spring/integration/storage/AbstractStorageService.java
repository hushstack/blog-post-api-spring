package com.cachewraith.blog_post_api_spring.integration.storage;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * The fixed part of an upload, shared by every backend.
 *
 * <p>Template Method. The sequence — reject empty, cap the size, sniff the content type from the
 * bytes and check it against the allowlist, generate the stored name, prove the bytes really decode
 * as an image — is
 * identical whether the object lands on local disk or in R2; only the write differs. It lives here
 * rather than in each implementation because it is the security-critical half, and two copies of it
 * would eventually stop agreeing (OWASP A05, A08).
 *
 * <p>Strategy was the other candidate and is what the {@link StorageService} interface already is:
 * this class adds the shared steps behind that seam, it does not replace it.
 */
@Slf4j
public abstract class AbstractStorageService implements StorageService {

    protected final StorageProperties properties;

    protected AbstractStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    /** The origin every stored URL is built from, without a trailing slash. */
    protected abstract String publicBaseUrl();

    /** Writes the object at {@code key}. The key is already generated and validated. */
    protected abstract void write(String key, byte[] content, String contentType);

    @Override
    public final String storeImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "File is empty");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }

        byte[] content = readFully(file);

        // The type comes from the bytes, not from the part's Content-Type header. The header is
        // whatever the client felt like sending — Postman sends application/octet-stream when its
        // MIME lookup fails, and an attacker sends image/png over anything at all — so it is
        // neither a reliable signal nor a trustworthy one (OWASP A08).
        String contentType = requireAllowedContentType(sniffContentType(content));
        if (file.getContentType() != null && !file.getContentType().startsWith(contentType)) {
            log.debug("Upload declared {} but is {}", file.getContentType(), contentType);
        }

        // The name is generated, never taken from the upload — a client-supplied filename is the
        // classic path-traversal vector, and on an object store it is equally a key-injection one.
        String key =
                requireSafeFolder(folder) + "/" + UUID.randomUUID() + "." + extensionFor(contentType);

        requireDecodableImage(content);

        write(key, content, contentType);
        return publicBaseUrl() + "/" + key;
    }

    /**
     * Maps a stored public URL back to its object key, or empty when this store does not own the
     * URL. Everything that reads, replaces or deletes goes through here, so a crafted URL cannot
     * reach an object outside the prefix this store wrote (OWASP A01).
     */
    protected final Optional<String> keyFor(String url) {
        String base = publicBaseUrl();
        if (url == null || base == null || !url.startsWith(base + "/")) {
            return Optional.empty();
        }
        String key = url.substring(base.length() + 1);
        if (key.isBlank() || key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    private String requireAllowedContentType(String contentType) {
        if (contentType == null || !properties.getAllowedContentTypes().contains(contentType)) {
            throw new BusinessException(
                    ErrorCode.INVALID_IMAGE,
                    "Unsupported image type; accepted: "
                            + String.join(", ", properties.getAllowedContentTypes()));
        }
        return contentType;
    }

    /**
     * The image type by magic number, or null when the bytes start like none of the four formats
     * this store handles. Signature-only: {@link #requireDecodableImage} is what proves the rest
     * of the file is what the first bytes claim.
     */
    static String sniffContentType(byte[] b) {
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(b, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        // RIFF <4-byte size> WEBP
        if (startsWith(b, 'R', 'I', 'F', 'F') && b.length >= 12
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] b, int... prefix) {
        if (b.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((b[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Callers pass an internal constant, so anything else is a bug rather than a user error. */
    private String requireSafeFolder(String folder) {
        if (folder == null || !folder.matches("[a-z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Illegal storage folder: " + folder);
        }
        return folder;
    }

    private byte[] readFully(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            log.error("Could not read uploaded file", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not store file");
        }
    }

    /** Decoding proves the bytes really are an image, rather than trusting the declared header. */
    private void requireDecodableImage(byte[] content) {
        try (ByteArrayInputStream probe = new ByteArrayInputStream(content)) {
            if (ImageIO.read(probe) == null) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE, "File is not a valid image");
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "File is not a valid image");
        }
    }

    static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new BusinessException(ErrorCode.INVALID_IMAGE, "Unsupported content type");
        };
    }

    /** The inverse of {@link #extensionFor}, for an object that must be rewritten under its key. */
    static String contentTypeForKey(String key) {
        int dot = key.lastIndexOf('.');
        String extension = dot < 0 ? "" : key.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
