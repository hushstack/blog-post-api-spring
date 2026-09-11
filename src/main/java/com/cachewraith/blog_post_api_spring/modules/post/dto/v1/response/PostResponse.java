package com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response;

import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response shape from spec section 6. {@code isOwner} is true when the viewer wrote the post —
 * always false for anonymous callers — so a client can show edit and delete controls without a
 * second request for the current user.
 */
public record PostResponse(
        UUID id,
        AuthorSummary author,
        String content,
        List<PostImageResponse> images,
        String visibility,
        boolean isOwner,
        Map<String, Long> reactionCounts,
        String viewerReaction,
        long commentCount,
        long repostCount,
        PostResponse originalPost,
        String shareUrl,
        Instant createdAt) {}
