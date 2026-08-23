package com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request;

import com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility;
import jakarta.validation.constraints.Size;

/** Text half of the multipart create; images arrive as separate parts. */
public record CreatePostRequest(
        @Size(max = 5000) String content, Visibility visibility) {}
