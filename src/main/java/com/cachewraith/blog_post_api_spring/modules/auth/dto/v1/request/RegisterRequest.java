package com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank
                @Size(min = 3, max = 32)
                @Pattern(
                        regexp = "^[a-zA-Z0-9_.]+$",
                        message = "may contain only letters, digits, underscore and dot")
                String username,
        @NotBlank @Size(min = 12, max = 128) String password,
        @Size(max = 100) String fullName) {}
