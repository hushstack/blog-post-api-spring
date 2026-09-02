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
     * Ant pattern the locally stored files are served under, derived from {@link #publicBaseUrl}
     * so the resource handler in {@code WebConfig} and the permit rule in {@code SecurityConfig}
     * cannot drift apart — two hand-written copies of "/files/**" would.
     *
     * <p>Only meaningful for {@link Driver#LOCAL}: an R2 bucket serves its own objects.
     */
    public String localFilesPattern() {
        String path = java.net.URI.create(publicBaseUrl).getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            // Serving from "/**" would put the whole application behind a static handler and
            // permit it anonymously. Refuse rather than guess (OWASP A01).
            throw new IllegalStateException(
                    "app.storage.public-base-url must include a path segment (e.g. .../files) "
                            + "when app.storage.driver=local, but was: " + publicBaseUrl);
        }
        return (path.endsWith("/") ? path.substring(0, path.length() - 1) : path) + "/**";
    }

    /**
     * A stored URL is built by concatenating the base with {@code "/" + key}, so a configured
     * trailing slash would produce a double one and then fail to resolve back to its key.
     */
    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

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
