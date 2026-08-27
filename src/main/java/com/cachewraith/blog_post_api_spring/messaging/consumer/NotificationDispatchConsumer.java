package com.cachewraith.blog_post_api_spring.messaging.consumer;

import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.messaging.event.NotificationDispatchEvent;
import com.cachewraith.blog_post_api_spring.modules.notification.entity.NotificationType;
import com.cachewraith.blog_post_api_spring.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Persists a notification row for each dispatched event.
 *
 * <p>Recording happens here rather than inline in the feature services so that a notification
 * failure cannot roll back the comment, repost or friend request that raised it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_DISPATCH)
    public void handle(NotificationDispatchEvent event) {
        NotificationType type;
        try {
            type = NotificationType.valueOf(event.type());
        } catch (IllegalArgumentException e) {
            // A type this build does not know is dropped, not retried: redelivering it would fail
            // identically forever and only fill the dead-letter queue (OWASP A10).
            log.warn("Dropping notification with unknown type {}", event.type());
            return;
        }
        notificationService.record(event.recipientId(), event.actorId(), type, event.targetId());
        log.debug("Recorded {} notification for recipient {}", type, event.recipientId());
    }
}
