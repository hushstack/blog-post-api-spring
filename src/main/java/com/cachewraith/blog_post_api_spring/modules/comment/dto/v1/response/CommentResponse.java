package com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.response;

import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Replies are one level deep and travel nested in {@code replies}, oldest first, so one page of
 * top-level comments is the whole thread for those comments. A reply's own {@code replies} is
 * always empty.
 */
public record CommentResponse(
        UUID id,
        UUID postId,
        AuthorSummary author,
        UUID parentCommentId,
        String content,
        long reactionCount,
        String viewerReaction,
        Instant createdAt,
        List<CommentResponse> replies) {}
