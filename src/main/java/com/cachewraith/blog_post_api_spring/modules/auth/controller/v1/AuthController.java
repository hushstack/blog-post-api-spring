package com.cachewraith.blog_post_api_spring.modules.auth.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.RateLimited;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ForgotPasswordRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.LoginRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.RefreshRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.RegisterRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ResetPasswordRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.VerifyOtpRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.RegisterResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.TokenPairResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersions.V1 + "/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @RateLimited(limit = 5, windowSeconds = 3600)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register and receive a verification code")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(authService.register(request));
    }

    /**
     * Takes {@code {email, code}} in the body rather than the code in the path. The code is a
     * secret: in a path it lands in access logs, proxy logs and Referer headers, and without the
     * email there is no way to scope the attempt counter to one account (OWASP A07, A09).
     */
    @PostMapping("/verify-otp")
    @RateLimited(limit = 10, windowSeconds = 600)
    @Operation(summary = "Verify a registration code and receive tokens")
    public ApiResponse<TokenPairResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ApiResponse.of(authService.verifyOtp(request));
    }

    @PostMapping("/login")
    @RateLimited(limit = 10, windowSeconds = 300)
    @Operation(summary = "Exchange credentials for a token pair")
    public ApiResponse<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of(authService.login(request));
    }

    @PostMapping("/refresh")
    @RateLimited(limit = 30, windowSeconds = 60)
    @Operation(summary = "Rotate a refresh token")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.of(authService.refresh(request));
    }

    // Not rate limited: logout needs a valid token to do anything, and throttling it would
    // leave a user unable to revoke a token they believe is compromised.
    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented access token")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring("Bearer ".length()).trim());
        }
        return ApiResponse.empty();
    }

    @PostMapping("/forgot-password")
    @RateLimited(limit = 3, windowSeconds = 3600)
    @Operation(summary = "Request a password reset")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.empty();
    }

    @PostMapping("/reset-password")
    @RateLimited(limit = 5, windowSeconds = 3600)
    @Operation(summary = "Reset a password with a valid token")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.empty();
    }
}
