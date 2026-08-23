package com.cachewraith.blog_post_api_spring.messaging.consumer;

import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.messaging.event.NotificationDispatchEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Fan-out point for notifications.
 *
 * <p>The spec's queue table calls for a notification row, but section 4 defines no Notification
 * entity and section 5 no endpoints to read them, so nothing is persisted yet — see the note in
 * CLAUDE.md. Adding the entity is a self-contained follow-up.
 */
@Slf4j
@Component
public class NotificationDispatchConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_DISPATCH)
    public void handle(NotificationDispatchEvent event) {
        log.info(
                "Notification {} for recipient {} from actor {}",
                event.type(),
                event.recipientId(),
                event.actorId());
    }
}
