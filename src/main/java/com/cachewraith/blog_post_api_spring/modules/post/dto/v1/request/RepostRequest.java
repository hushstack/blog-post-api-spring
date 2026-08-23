package com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request;

import jakarta.validation.constraints.Size;

public record RepostRequest(@Size(max = 5000) String content) {}
