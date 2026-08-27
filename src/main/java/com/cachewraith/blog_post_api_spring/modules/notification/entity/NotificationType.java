package com.cachewraith.blog_post_api_spring.modules.notification.entity;

/**
 * The events that raise a notification today. Values are persisted as strings, so an entry may be
 * added but never renamed or reordered without a migration.
 *
 * <p>There is deliberately no REACTION value: notifying a reaction would require ReactionService to
 * resolve the target's owner through PostService, reintroducing exactly the cycle that
 * {@code ReactionSyncConsumer} exists to avoid.
 */
public enum NotificationType {
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    COMMENT,
    REPOST
}
