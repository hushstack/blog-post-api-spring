package com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request;

import com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(@Size(max = 5000) String content, Visibility visibility) {}
