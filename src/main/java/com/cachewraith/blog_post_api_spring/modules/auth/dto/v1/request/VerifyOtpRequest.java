package com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The email binds the code to one account. Without it the server would have to scan every
 * {@code otp:*} key, which is both racy and unable to rate-limit per user (OWASP A06, A07).
 */
public record VerifyOtpRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be a 6-digit code") String code) {}
