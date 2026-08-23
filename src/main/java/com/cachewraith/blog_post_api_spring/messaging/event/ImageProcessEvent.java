package com.cachewraith.blog_post_api_spring.messaging.event;

import java.util.List;
import java.util.UUID;

public record ImageProcessEvent(UUID postId, UUID ownerId, List<String> urls) {}
