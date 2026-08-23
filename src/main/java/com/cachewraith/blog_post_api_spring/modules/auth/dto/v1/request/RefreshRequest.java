package com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {}
