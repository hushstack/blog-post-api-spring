package com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response;

import java.util.Map;

/** {@code counts} is keyed by reaction type; {@code viewerReaction} is null when not signed in. */
public record ReactionSummaryResponse(
        Map<String, Long> counts, long total, String viewerReaction) {}
