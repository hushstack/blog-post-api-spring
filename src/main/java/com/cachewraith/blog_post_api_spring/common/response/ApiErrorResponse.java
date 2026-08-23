package com.cachewraith.blog_post_api_spring.common.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Error envelope. Carries the {@code ErrorCode} name and a safe message only — never a stack
 * trace, SQL fragment, or any other internal detail (OWASP A09/A10).
 */
public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        Map<String, List<String>> fieldErrors,
        String path,
        Instant timestamp) {

    public static ApiErrorResponse of(String code, String message, String path) {
        return new ApiErrorResponse(false, code, message, null, path, Instant.now());
    }

    public static ApiErrorResponse withFields(
            String code, String message, Map<String, List<String>> fieldErrors, String path) {
        return new ApiErrorResponse(false, code, message, fieldErrors, path, Instant.now());
    }
}
