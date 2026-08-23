package com.cachewraith.blog_post_api_spring.messaging.event;

import java.util.UUID;

public record ReactionSyncEvent(String targetType, UUID targetId) {}
