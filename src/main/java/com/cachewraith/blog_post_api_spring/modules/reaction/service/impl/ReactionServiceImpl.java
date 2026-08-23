package com.cachewraith.blog_post_api_spring.modules.reaction.service.impl;

import com.cachewraith.blog_post_api_spring.common.constant.AppConstants;
import com.cachewraith.blog_post_api_spring.common.exception.ResourceNotFoundException;
import com.cachewraith.blog_post_api_spring.messaging.event.ReactionSyncEvent;
import com.cachewraith.blog_post_api_spring.messaging.producer.EventPublisher;
import com.cachewraith.blog_post_api_spring.modules.comment.repository.CommentRepository;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostVisibilityService;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.request.ReactionRequest;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response.ReactionSummaryResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.Reaction;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.TargetType;
import com.cachewraith.blog_post_api_spring.modules.reaction.repository.ReactionRepository;
import com.cachewraith.blog_post_api_spring.modules.reaction.service.ReactionService;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reactions, with Postgres as the source of truth and Redis as a counter cache.
 *
 * <p>Postgres holds one row per (target, user) behind a unique constraint, so a double-click cannot
 * produce two reactions no matter how the requests interleave. Redis mirrors the per-type counts
 * for cheap reads and is rebuilt from the database whenever it is missing, so a flushed cache
 * degrades to a slower read rather than a wrong number.
 */
@Service
@RequiredArgsConstructor
public class ReactionServiceImpl implements ReactionService {

    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final PostVisibilityService postVisibilityService;
    private final StringRedisTemplate redis;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public ReactionSummaryResponse upsert(
            UUID userId, TargetType targetType, UUID targetId, ReactionRequest request) {
        assertTargetReachable(targetType, targetId, userId);

        Optional<Reaction> existing =
                reactionRepository.findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId);

        if (existing.isPresent()) {
            Reaction reaction = existing.get();
            reaction.setType(request.type());
            reactionRepository.save(reaction);
        } else {
            try {
                reactionRepository.save(
                        Reaction.builder()
                                .targetType(targetType)
                                .targetId(targetId)
                                .userId(userId)
                                .type(request.type())
                                .build());
            } catch (DataIntegrityViolationException ex) {
                // Lost the race against a concurrent first reaction; treat as an update.
                reactionRepository
                        .findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId)
                        .ifPresent(
                                reaction -> {
                                    reaction.setType(request.type());
                                    reactionRepository.save(reaction);
                                });
            }
        }

        invalidateCounts(targetType, targetId);
        eventPublisher.syncReactions(new ReactionSyncEvent(targetType.name(), targetId));
        return summary(targetType, targetId, userId);
    }

    @Override
    @Transactional
    public ReactionSummaryResponse remove(UUID userId, TargetType targetType, UUID targetId) {
        assertTargetReachable(targetType, targetId, userId);
        reactionRepository.deleteByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId);

        invalidateCounts(targetType, targetId);
        eventPublisher.syncReactions(new ReactionSyncEvent(targetType.name(), targetId));
        return summary(targetType, targetId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReactionSummaryResponse summary(TargetType targetType, UUID targetId, UUID viewerId) {
        Map<String, Long> counts = countsFor(targetType, targetId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        String viewerReaction =
                viewerId == null
                        ? null
                        : reactionRepository
                                .findByTargetTypeAndTargetIdAndUserId(targetType, targetId, viewerId)
                                .map(reaction -> reaction.getType().name())
                                .orElse(null);

        return new ReactionSummaryResponse(counts, total, viewerReaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ReactionSummaryResponse> summaries(
            TargetType targetType, Collection<UUID> targetIds, UUID viewerId) {
        Map<UUID, ReactionSummaryResponse> result = new LinkedHashMap<>();
        if (targetIds.isEmpty()) {
            return result;
        }

        // Two queries total, regardless of page size: one for the counts, one for the viewer's own
        // reactions. Reading the cache per target here would be N round trips for no benefit.
        Map<UUID, Map<String, Long>> countsByTarget = new HashMap<>();
        for (Object[] row : reactionRepository.countGroupedByTypeForTargets(targetType, targetIds)) {
            UUID targetId = (UUID) row[0];
            countsByTarget
                    .computeIfAbsent(targetId, k -> new HashMap<>())
                    .put(row[1].toString(), ((Number) row[2]).longValue());
        }

        Map<UUID, String> viewerReactions = new HashMap<>();
        if (viewerId != null) {
            reactionRepository
                    .findByTargetTypeAndTargetIdInAndUserId(targetType, targetIds, viewerId)
                    .forEach(r -> viewerReactions.put(r.getTargetId(), r.getType().name()));
        }

        for (UUID targetId : targetIds) {
            Map<String, Long> counts = countsByTarget.getOrDefault(targetId, Map.of());
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            result.put(
                    targetId,
                    new ReactionSummaryResponse(counts, total, viewerReactions.get(targetId)));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public long totalFor(TargetType targetType, UUID targetId) {
        return reactionRepository.countByTargetTypeAndTargetId(targetType, targetId);
    }

    /** A reaction target must itself be visible, or reactions become an oracle (OWASP A01). */
    private void assertTargetReachable(TargetType targetType, UUID targetId, UUID userId) {
        switch (targetType) {
            case POST -> postVisibilityService.requireVisible(targetId, userId);
            case COMMENT -> {
                var comment =
                        commentRepository
                                .findById(targetId)
                                .orElseThrow(() -> new ResourceNotFoundException("Comment"));
                postVisibilityService.requireVisible(comment.getPostId(), userId);
            }
        }
    }

    private Map<String, Long> countsFor(TargetType targetType, UUID targetId) {
        String key = countsKey(targetType, targetId);
        Map<Object, Object> cached = redis.opsForHash().entries(key);

        if (!cached.isEmpty()) {
            Map<String, Long> counts = new HashMap<>();
            cached.forEach((k, v) -> counts.put(k.toString(), Long.parseLong(v.toString())));
            return counts;
        }

        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : reactionRepository.countGroupedByType(targetType, targetId)) {
            counts.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        if (!counts.isEmpty()) {
            Map<String, String> asStrings = new HashMap<>();
            counts.forEach((k, v) -> asStrings.put(k, Long.toString(v)));
            redis.opsForHash().putAll(key, asStrings);
        }
        return counts;
    }

    private void invalidateCounts(TargetType targetType, UUID targetId) {
        redis.delete(countsKey(targetType, targetId));
    }

    private String countsKey(TargetType targetType, UUID targetId) {
        return AppConstants.KEY_POST_REACTIONS + targetType.name().toLowerCase() + ":" + targetId;
    }
}
