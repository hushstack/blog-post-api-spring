package com.cachewraith.blog_post_api_spring.modules.notification.service;

import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.notification.dto.v1.response.NotificationResponse;
import com.cachewraith.blog_post_api_spring.modules.notification.entity.NotificationType;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /**
     * Persists a notification. Called from the {@code notification.dispatch} consumer, not inline
     * from the feature services — a failure to record a notification must not roll back the comment
     * or friend request that caused it.
     */
    void record(UUID recipientId, UUID actorId, NotificationType type, UUID targetId);

    PageResponse<NotificationResponse> list(UUID recipientId, boolean unreadOnly, Pageable pageable);

    long unreadCount(UUID recipientId);

    NotificationResponse markRead(UUID notificationId, UUID recipientId);

    int markAllRead(UUID recipientId);
}
