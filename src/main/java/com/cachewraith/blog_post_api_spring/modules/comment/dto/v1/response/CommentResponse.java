package com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.response;

import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID postId,
        AuthorSummary author,
        UUID parentCommentId,
        String content,
        long reactionCount,
        String viewerReaction,
        Instant createdAt) {}
