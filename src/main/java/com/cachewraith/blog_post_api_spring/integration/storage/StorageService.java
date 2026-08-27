package com.cachewraith.blog_post_api_spring.integration.storage;

import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Object storage behind one seam.
 *
 * <p>An interface earns its place here because there are two real implementations in play — local
 * disk for development, an object store in production — not a hypothetical third.
 */
public interface StorageService {

    /** Stores the file and returns its public URL. Rejects anything that is not a safe image. */
    String storeImage(MultipartFile file, String folder);

    /**
     * Reads back a previously stored object, or empty if this store does not own the URL or the
     * object is gone. Used by the image.process stage to re-encode what was uploaded.
     */
    Optional<byte[]> read(String url);

    /** Overwrites the object at an existing URL, leaving the URL — and every row referencing it — valid. */
    void replace(String url, byte[] content);

    void delete(String url);
}
