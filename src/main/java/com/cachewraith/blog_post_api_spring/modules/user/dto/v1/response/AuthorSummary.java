package com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response;

import java.util.UUID;

/** Embedded author block used by post and comment responses. */
/** The user as shown next to something they did — a post, a comment, a reaction. */
public record AuthorSummary(UUID id, String username, String fullName, String avatarUrl) {}
