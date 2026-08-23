package com.cachewraith.blog_post_api_spring.security.jwt;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * HMAC signing secret, supplied from the environment. Must decode to at least 256 bits for
     * HS256; {@link JwtProvider} refuses to start otherwise (OWASP A04).
     */
    @NotBlank private String secret;

    private String issuer = "blog-post-api";

    private Duration accessTokenTtl = Duration.ofMinutes(15);

    private Duration refreshTokenTtl = Duration.ofDays(7);
}
