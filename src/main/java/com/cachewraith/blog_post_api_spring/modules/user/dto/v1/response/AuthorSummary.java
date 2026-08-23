package com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response;

import java.util.UUID;

/** Embedded author block used by post and comment responses. */
public record AuthorSummary(UUID id, String username, String avatarUrl) {}
