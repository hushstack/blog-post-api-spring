package com.cachewraith.blog_post_api_spring.modules.notification.dto.v1.response;

import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        AuthorSummary actor,
        UUID targetId,
        boolean read,
        Instant readAt,
        Instant createdAt) {}
