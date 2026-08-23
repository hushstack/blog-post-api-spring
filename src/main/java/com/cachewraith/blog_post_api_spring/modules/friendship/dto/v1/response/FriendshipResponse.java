package com.cachewraith.blog_post_api_spring.modules.friendship.dto.v1.response;

import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import java.time.Instant;
import java.util.UUID;

public record FriendshipResponse(
        UUID id, AuthorSummary user, String status, Instant createdAt, Instant respondedAt) {}
