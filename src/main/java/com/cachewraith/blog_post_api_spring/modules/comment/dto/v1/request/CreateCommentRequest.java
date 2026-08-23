package com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCommentRequest(
        @NotBlank @Size(max = 2000) String content, UUID parentCommentId) {}
