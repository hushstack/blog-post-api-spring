package com.cachewraith.blog_post_api_spring.integration.storage;

import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String basePath = "./uploads";

    private String publicBaseUrl = "http://localhost:8080/files";

    private long maxFileSizeBytes = 5L * 1024 * 1024;

    /** Allowlist, not a blocklist — anything not named here is rejected (OWASP A08). */
    private Set<String> allowedContentTypes =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp", "gif");

    /**
     * Longest edge kept by the image.process stage. Anything larger is downscaled in place; smaller
     * images are left untouched rather than re-encoded for no gain.
     */
    private int maxDimensionPixels = 1600;
}
