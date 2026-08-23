package com.cachewraith.blog_post_api_spring.modules.reaction.repository;

import com.cachewraith.blog_post_api_spring.modules.reaction.entity.Reaction;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.TargetType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    Optional<Reaction> findByTargetTypeAndTargetIdAndUserId(
            TargetType targetType, UUID targetId, UUID userId);

    void deleteByTargetTypeAndTargetIdAndUserId(TargetType targetType, UUID targetId, UUID userId);

    long countByTargetTypeAndTargetId(TargetType targetType, UUID targetId);

    /** Counts per reaction type for one target, in a single query. */
    @Query(
            """
            select r.type, count(r) from Reaction r
            where r.targetType = :targetType and r.targetId = :targetId
            group by r.type
            """)
    List<Object[]> countGroupedByType(
            @Param("targetType") TargetType targetType, @Param("targetId") UUID targetId);

    /** {targetId, type, count} for many targets at once — one query for a whole page. */
    @Query(
            """
            select r.targetId, r.type, count(r) from Reaction r
            where r.targetType = :targetType and r.targetId in :targetIds
            group by r.targetId, r.type
            """)
    List<Object[]> countGroupedByTypeForTargets(
            @Param("targetType") TargetType targetType,
            @Param("targetIds") Collection<UUID> targetIds);

    List<Reaction> findByTargetTypeAndTargetIdInAndUserId(
            TargetType targetType, Collection<UUID> targetIds, UUID userId);
}
