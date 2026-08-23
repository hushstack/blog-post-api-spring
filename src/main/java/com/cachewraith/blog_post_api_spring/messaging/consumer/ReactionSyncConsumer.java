package com.cachewraith.blog_post_api_spring.messaging.consumer;

import com.cachewraith.blog_post_api_spring.config.RabbitMQConfig;
import com.cachewraith.blog_post_api_spring.messaging.event.ReactionSyncEvent;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostService;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.TargetType;
import com.cachewraith.blog_post_api_spring.modules.reaction.service.ReactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Flushes reaction counters onto the post row.
 *
 * <p>The consumer orchestrates both modules rather than either service calling the other, which is
 * what keeps {@code PostService} and {@code ReactionService} free of a circular dependency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactionSyncConsumer {

    private final ReactionService reactionService;
    private final PostService postService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_REACTION_SYNC)
    public void handle(ReactionSyncEvent event) {
        TargetType targetType = TargetType.valueOf(event.targetType());
        if (targetType != TargetType.POST) {
            return; // comment counters are read live; nothing denormalised to sync
        }
        long total = reactionService.totalFor(targetType, event.targetId());
        postService.setReactionCount(event.targetId(), total);
        log.debug("Synced reaction count {} for post {}", total, event.targetId());
    }
}
