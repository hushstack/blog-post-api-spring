package com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response;

import java.util.UUID;

/** {@code id} is what {@code PUT /posts/{id}} takes in {@code removeImageIds}. */
public record PostImageResponse(UUID id, String url, int position) {}
