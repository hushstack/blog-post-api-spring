package com.cachewraith.blog_post_api_spring.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Allowed origins come from configuration — never a wildcard alongside credentials (OWASP A02). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:3000");
}
