package com.cachewraith.blog_post_api_spring.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Canonical public base for share links. Configured, never derived from a request Host header. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.share")
public class ShareProperties {

    private String baseUrl = "http://localhost:8080";
}
