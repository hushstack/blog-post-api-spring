package com.cachewraith.blog_post_api_spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Drives @CreatedDate / @LastModifiedDate on {@code BaseEntity}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
