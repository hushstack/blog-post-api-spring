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

    /** Which backend {@code StorageConfig} builds. */
    public enum Driver {
        LOCAL,
        R2
    }

    private Driver driver = Driver.LOCAL;

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

    private final R2 r2 = new R2();

    /**
     * Cloudflare R2. Two hosts are in play and they are not interchangeable: {@code endpoint} is the
     * private S3 API host that the SDK signs requests against, {@code publicUrl} is the r2.dev (or
     * custom) domain that readers fetch the object from and the only one that ever reaches a client.
     */
    @Getter
    @Setter
    public static class R2 {

        private String accessKeyId;

        private String secretAccessKey;

        private String bucket;

        /** R2 is single-region; {@code auto} is what Cloudflare documents. */
        private String region = "auto";

        private String endpoint;

        private String publicUrl;

        /** R2 supports both; path style keeps the bucket out of the hostname. */
        private boolean pathStyleAccess = true;

        /**
         * Sent on every upload. Deliberately short and deliberately not {@code immutable}: the
         * image.process stage rewrites the object at the same key, so a long-lived cache entry would
         * pin the full-size original in front of the downscaled one.
         */
        private String cacheControl = "public, max-age=3600";

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = trimTrailingSlash(publicUrl);
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = trimTrailingSlash(endpoint);
        }
    }
}
