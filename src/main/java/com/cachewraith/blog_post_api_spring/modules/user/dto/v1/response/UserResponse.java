package com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response;

import java.time.Instant;
import java.util.UUID;

/** Own-profile view. Includes email; the public view deliberately does not. */
public record UserResponse(
        UUID id,
        String email,
        String username,
        String fullName,
        String bio,
        String avatarUrl,
        String status,
        Instant createdAt) {}
