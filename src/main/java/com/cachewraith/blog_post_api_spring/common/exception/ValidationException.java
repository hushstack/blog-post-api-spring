package com.cachewraith.blog_post_api_spring.common.exception;

public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
