package com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Public profile. Email and status are omitted on purpose — {@code GET /users/{id}} is reachable
 * without a token, so anything here is world-readable (OWASP A01, A04).
 */
public record PublicUserResponse(
        UUID id, String username, String fullName, String bio, String avatarUrl, Instant createdAt) {}
