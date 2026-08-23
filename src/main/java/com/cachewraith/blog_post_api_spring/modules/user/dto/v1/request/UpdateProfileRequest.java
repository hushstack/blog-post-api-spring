package com.cachewraith.blog_post_api_spring.modules.user.dto.v1.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100) String fullName,
        @Size(max = 500) String bio,
        @Size(min = 3, max = 32)
                @Pattern(
                        regexp = "^[a-zA-Z0-9_.]+$",
                        message = "may contain only letters, digits, underscore and dot")
                String username) {}
