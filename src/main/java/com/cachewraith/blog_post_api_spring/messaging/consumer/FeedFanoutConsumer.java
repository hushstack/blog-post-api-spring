package com.cachewraith.blog_post_api_spring.messaging.consumer;

import com.cachewraith.blog_post_api_spring.common.constant.AppConstants;
import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.messaging.event.FeedFanoutEvent;
import com.cachewraith.blog_post_api_spring.modules.friendship.service.FriendshipService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Invalidates each friend's cached feed page when a new post lands.
 *
 * <p>Invalidating rather than pushing the id keeps the cache from ever serving a post the viewer is
 * no longer allowed to see — the next read re-runs the visibility-filtered query (OWASP A01).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedFanoutConsumer {

    private final FriendshipService friendshipService;
    private final StringRedisTemplate redis;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_FEED_FANOUT)
    public void handle(FeedFanoutEvent event) {
        List<UUID> friendIds = friendshipService.friendIds(event.authorId());
        friendIds.forEach(id -> redis.delete(AppConstants.KEY_FEED + id));
        redis.delete(AppConstants.KEY_FEED + event.authorId());
        log.debug("Invalidated feed cache for {} follower(s) of {}", friendIds.size(), event.authorId());
    }
}
