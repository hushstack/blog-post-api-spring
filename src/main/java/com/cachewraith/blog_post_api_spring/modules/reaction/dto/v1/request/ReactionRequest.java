package com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.request;

import com.cachewraith.blog_post_api_spring.modules.reaction.entity.ReactionType;
import jakarta.validation.constraints.NotNull;

public record ReactionRequest(@NotNull ReactionType type) {

    /** What an empty toggle request means. */
    public static ReactionRequest like() {
        return new ReactionRequest(ReactionType.LIKE);
    }
}
