package com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Email only. The purpose is derived from the account's state on the server — a pending account
 * can only need a REGISTER code, an active one a RESET_PASSWORD code — so there is nothing for a
 * caller to choose, and nothing for one to choose wrongly.
 */
public record ResendOtpRequest(@NotBlank @Email String email) {}
