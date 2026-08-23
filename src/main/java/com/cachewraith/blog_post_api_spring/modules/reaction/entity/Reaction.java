package com.cachewraith.blog_post_api_spring.modules.reaction.entity;

import com.cachewraith.blog_post_api_spring.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One reaction per user per target. The unique constraint is what makes the upsert in
 * {@code PUT /reactions/{targetType}/{targetId}} safe under concurrent requests — the database,
 * not the service, is the arbiter.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "reactions",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_reaction_target_user",
                        columnNames = {"target_type", "target_id", "user_id"}),
        indexes = @Index(name = "ix_reactions_target", columnList = "target_type, target_id"))
public class Reaction extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReactionType type;
}
