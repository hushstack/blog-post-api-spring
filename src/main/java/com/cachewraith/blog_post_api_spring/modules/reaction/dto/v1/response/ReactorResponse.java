package com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response;

import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import com.cachewraith.blog_post_api_spring.modules.friendship.dto.v1.response.FriendStatus;
import java.time.Instant;

/**
 * One row of "who reacted": the user, what they chose, when, and where the viewer stands with
 * them. {@code friendStatus} is null for an anonymous viewer, who has no standing with anyone.
 */
public record ReactorResponse(
        AuthorSummary user, String type, Instant reactedAt, FriendStatus friendStatus) {}
