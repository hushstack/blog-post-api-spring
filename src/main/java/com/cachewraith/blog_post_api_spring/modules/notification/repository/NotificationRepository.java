package com.cachewraith.blog_post_api_spring.modules.notification.repository;

import com.cachewraith.blog_post_api_spring.modules.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    /**
     * Marks every unread row for one recipient in a single statement. Loading them to set a field
     * each would be one update per row for no benefit — nothing here needs the entities.
     */
    @Modifying
    @Query(
            """
            update Notification n set n.readAt = :now, n.updatedAt = :now
            where n.recipientId = :recipientId and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
