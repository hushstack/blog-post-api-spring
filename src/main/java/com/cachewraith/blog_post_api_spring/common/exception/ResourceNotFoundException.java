package com.cachewraith.blog_post_api_spring.common.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String what) {
        super(ErrorCode.RESOURCE_NOT_FOUND, what + " not found");
    }
}
