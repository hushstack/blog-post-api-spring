package com.cachewraith.blog_post_api_spring.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED("Request validation failed", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("Resource not found", HttpStatus.NOT_FOUND),

    EMAIL_ALREADY_USED("Email is already registered", HttpStatus.CONFLICT),
    USERNAME_ALREADY_USED("Username is already taken", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("Invalid email or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_VERIFIED("Account is not verified", HttpStatus.FORBIDDEN),
    ACCOUNT_SUSPENDED("Account is suspended", HttpStatus.FORBIDDEN),

    OTP_INVALID("Invalid or expired code", HttpStatus.BAD_REQUEST),
    OTP_TOO_MANY_ATTEMPTS("Too many attempts, request a new code", HttpStatus.TOO_MANY_REQUESTS),
    RESET_TOKEN_INVALID("Invalid or expired reset token", HttpStatus.BAD_REQUEST),

    TOKEN_INVALID("Invalid token", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("Token has expired", HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED("Token has been revoked", HttpStatus.UNAUTHORIZED),

    RATE_LIMIT_EXCEEDED("Too many requests, slow down", HttpStatus.TOO_MANY_REQUESTS),

    ACCESS_DENIED("You are not allowed to perform this action", HttpStatus.FORBIDDEN),
    POST_NOT_VISIBLE("Post is not visible to you", HttpStatus.FORBIDDEN),

    FRIENDSHIP_EXISTS("A friendship or request already exists", HttpStatus.CONFLICT),
    FRIENDSHIP_SELF("You cannot befriend yourself", HttpStatus.BAD_REQUEST),
    FRIEND_REQUEST_NOT_PENDING("Request is no longer pending", HttpStatus.CONFLICT),

    INVALID_IMAGE("Unsupported or malformed image", HttpStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE("Uploaded file is too large", HttpStatus.PAYLOAD_TOO_LARGE),

    INTERNAL_ERROR("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String defaultMessage;
    private final HttpStatus status;
}
