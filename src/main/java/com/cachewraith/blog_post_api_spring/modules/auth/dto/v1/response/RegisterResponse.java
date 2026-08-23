package com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response;

import java.util.UUID;

public record RegisterResponse(UUID userId, String email, String message) {}
