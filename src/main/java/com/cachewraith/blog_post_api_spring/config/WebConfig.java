package com.cachewraith.blog_post_api_spring.config;

import com.cachewraith.blog_post_api_spring.security.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC wiring. Registers the interceptor chain; the security chain is in {@link SecurityConfig}. */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Registered for every path; the interceptor itself is a no-op on handlers that carry no
        // @RateLimited annotation, which keeps the limit declared at the handler rather than here.
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/**");
    }
}
