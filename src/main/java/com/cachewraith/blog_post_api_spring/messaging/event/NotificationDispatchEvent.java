package com.cachewraith.blog_post_api_spring.messaging.event;

import java.util.UUID;

public record NotificationDispatchEvent(
        UUID recipientId, UUID actorId, String type, UUID targetId) {}
