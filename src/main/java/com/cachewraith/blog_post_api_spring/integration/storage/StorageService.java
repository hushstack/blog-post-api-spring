package com.cachewraith.blog_post_api_spring.integration.storage;

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

    void delete(String url);
}
