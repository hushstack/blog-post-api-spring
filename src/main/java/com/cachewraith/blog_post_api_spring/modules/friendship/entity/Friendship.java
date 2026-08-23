package com.cachewraith.blog_post_api_spring.modules.friendship.entity;

import com.cachewraith.blog_post_api_spring.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "friendships",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_friendship_pair",
                        columnNames = {"requester_id", "addressee_id"}),
        indexes = {
            @Index(name = "ix_friendship_requester", columnList = "requester_id, status"),
            @Index(name = "ix_friendship_addressee", columnList = "addressee_id, status")
        })
public class Friendship extends BaseEntity {

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
