package com.cachewraith.blog_post_api_spring.messaging.producer;

import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.messaging.event.FeedFanoutEvent;
import com.cachewraith.blog_post_api_spring.messaging.event.ImageProcessEvent;
import com.cachewraith.blog_post_api_spring.messaging.event.NotificationDispatchEvent;
import com.cachewraith.blog_post_api_spring.messaging.event.OtpSendEvent;
import com.cachewraith.blog_post_api_spring.messaging.event.ReactionSyncEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Single entry point for publishing. Routing keys match the queue names in spec section 3. */
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void sendOtp(OtpSendEvent event) {
        publish(RabbitMQConfig.QUEUE_OTP_SEND, event);
    }

    public void processImages(ImageProcessEvent event) {
        publish(RabbitMQConfig.QUEUE_IMAGE_PROCESS, event);
    }

    public void fanOutFeed(FeedFanoutEvent event) {
        publish(RabbitMQConfig.QUEUE_FEED_FANOUT, event);
    }

    public void syncReactions(ReactionSyncEvent event) {
        publish(RabbitMQConfig.QUEUE_REACTION_SYNC, event);
    }

    public void dispatchNotification(NotificationDispatchEvent event) {
        publish(RabbitMQConfig.QUEUE_NOTIFICATION_DISPATCH, event);
    }

    private void publish(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
    }
}
