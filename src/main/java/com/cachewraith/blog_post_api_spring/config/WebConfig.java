package com.cachewraith.blog_post_api_spring.config;

import com.cachewraith.blog_post_api_spring.integration.storage.StorageProperties;
import com.cachewraith.blog_post_api_spring.security.ratelimit.RateLimitInterceptor;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC wiring. Registers the interceptor chain; the security chain is in {@link SecurityConfig}. */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final StorageProperties storageProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Registered for every path; the interceptor itself is a no-op on handlers that carry no
        // @RateLimited annotation, which keeps the limit declared at the handler rather than here.
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/**");
    }

    /**
     * Serves the local storage directory, so the URLs {@code LocalStorageService} hands out
     * actually resolve. Registered only for the local driver — an R2 bucket serves its own
     * objects, and mounting a directory that nothing writes to would be a needless surface.
     *
     * <p>Spring's resource handler resolves each request under the configured location and
     * rejects anything that escapes it, which is what keeps a crafted {@code ../} out (OWASP A01).
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (storageProperties.getDriver() != StorageProperties.Driver.LOCAL) {
            return;
        }
        String root = Path.of(storageProperties.getBasePath()).toAbsolutePath().normalize().toString();
        registry
                .addResourceHandler(storageProperties.localFilesPattern())
                .addResourceLocations("file:" + root + "/");
    }
}
