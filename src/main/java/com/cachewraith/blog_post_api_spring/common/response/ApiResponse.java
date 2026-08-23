package com.cachewraith.blog_post_api_spring.common.response;

import java.time.Instant;

/**
 * Envelope for every successful endpoint. Mutating endpoints return the DTO inside {@code data};
 * entities never appear here.
 */
public record ApiResponse<T>(boolean success, T data, Instant timestamp) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data, Instant.now());
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(true, null, Instant.now());
    }
}
