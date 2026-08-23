package com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response;

import java.time.Instant;

public record TokenPairResponse(
        String accessToken, String refreshToken, String tokenType, Instant accessTokenExpiresAt) {

    public static TokenPairResponse of(String access, String refresh, Instant expiry) {
        return new TokenPairResponse(access, refresh, "Bearer", expiry);
    }
}
