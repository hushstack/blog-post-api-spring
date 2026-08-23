package com.cachewraith.blog_post_api_spring.modules.reaction.service;

import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.request.ReactionRequest;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response.ReactionSummaryResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.TargetType;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface ReactionService {

    ReactionSummaryResponse upsert(
            UUID userId, TargetType targetType, UUID targetId, ReactionRequest request);

    ReactionSummaryResponse remove(UUID userId, TargetType targetType, UUID targetId);

    ReactionSummaryResponse summary(TargetType targetType, UUID targetId, UUID viewerId);

    /** Batch variant, so rendering a page of posts does not issue one query per post. */
    Map<UUID, ReactionSummaryResponse> summaries(
            TargetType targetType, Collection<UUID> targetIds, UUID viewerId);

    /** Authoritative count straight from the database, used by the sync consumer. */
    long totalFor(TargetType targetType, UUID targetId);
}
