package com.cachewraith.blog_post_api_spring.modules.notification.entity;

import com.cachewraith.blog_post_api_spring.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A notification raised for {@code recipientId} by something {@code actorId} did.
 *
 * <p>{@code targetId} is deliberately untyped: what it points at is implied by {@code type}
 * (a friendship, a comment, a post). Like {@code Reaction.targetId} it therefore carries no foreign
 * key — a single column cannot reference three tables.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "notifications",
        indexes = {
            @Index(name = "ix_notifications_recipient_created", columnList = "recipient_id, created_at"),
            @Index(name = "ix_notifications_recipient_unread", columnList = "recipient_id, read_at")
        })
public class Notification extends BaseEntity {

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    /** Null for a notification raised by the system rather than by another user. */
    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "target_id")
    private UUID targetId;

    /** Null while unread. Storing the instant rather than a flag keeps "when" for free. */
    @Column(name = "read_at")
    private Instant readAt;
}
