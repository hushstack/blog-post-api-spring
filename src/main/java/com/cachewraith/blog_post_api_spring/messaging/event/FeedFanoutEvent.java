package com.cachewraith.blog_post_api_spring.messaging.event;

import java.util.UUID;

public record FeedFanoutEvent(UUID postId, UUID authorId) {}
