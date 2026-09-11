package com.cachewraith.blog_post_api_spring.modules.reaction.service;

import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.request.ReactionRequest;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response.ReactionSummaryResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.TargetType;
import java.util.Collection;
import java.util.Map;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response.ReactorResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.ReactionType;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface ReactionService {

    /**
     * The one mutation. No reaction yet → added; the same type again → removed; a different type
     * → changed. The returned {@code viewerReaction} says which state the caller ended in.
     */
    ReactionSummaryResponse toggle(
            UUID userId, TargetType targetType, UUID targetId, ReactionRequest request);

    ReactionSummaryResponse summary(TargetType targetType, UUID targetId, UUID viewerId);

    /** Who reacted, newest first; {@code type} narrows to one reaction type, null means all. */
    PageResponse<ReactorResponse> reactors(
            TargetType targetType, UUID targetId, ReactionType type, UUID viewerId, Pageable pageable);

    /** Batch variant, so rendering a page of posts does not issue one query per post. */
    Map<UUID, ReactionSummaryResponse> summaries(
            TargetType targetType, Collection<UUID> targetIds, UUID viewerId);

    /** Authoritative count straight from the database, used by the sync consumer. */
    long totalFor(TargetType targetType, UUID targetId);
}
