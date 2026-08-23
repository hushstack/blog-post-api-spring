package com.cachewraith.blog_post_api_spring.messaging.consumer;

import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.messaging.event.ImageProcessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Resize/optimise pass over uploaded images.
 *
 * <p>The upload path already stores the originals and their {@code PostImage} rows synchronously,
 * so a post is never left without its images if this consumer is down; this stage only replaces
 * them with optimised derivatives.
 */
@Slf4j
@Component
public class ImageProcessConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_IMAGE_PROCESS)
    public void handle(ImageProcessEvent event) {
        log.info("Processing {} image(s) for post {}", event.urls().size(), event.postId());
        // TODO: resize/optimise and replace the stored derivatives.
    }
}
