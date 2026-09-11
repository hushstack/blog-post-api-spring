package com.cachewraith.blog_post_api_spring.modules.auth.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.RateLimited;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ForgotPasswordRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.LoginRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.RefreshRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.RegisterRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ResendOtpRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ResetPasswordRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.VerifyOtpRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.RegisterResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.TokenPairResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.VerifyOtpResponse;
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
     *
     * <p>One endpoint for every OTP, and the account's state picks the flow — nothing in the body
     * does. Pending account: a REGISTER code, verified into a token pair. Active account: a
     * RESET_PASSWORD code, verified into a reset token and no session, since a code that proves
     * someone can read the mailbox must not by itself log them in (OWASP A07).
     */
    @PostMapping("/verify-otp")
    @RateLimited(limit = 10, windowSeconds = 600)
    @Operation(summary = "Verify an OTP — tokens while pending, a reset token once active")
    public ApiResponse<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ApiResponse.of(authService.verifyOtp(request));
    }

    /**
     * Email only; the server picks the purpose from the account's state. Always 200: a different
     * answer for an unknown address would disclose which addresses are registered (OWASP A07).
     * Two limits apply — this per-client one, and a per-account cooldown in {@code OtpService}
     * that no number of clients can add up past (OWASP A06).
     */
    @PostMapping("/resend-otp")
    @RateLimited(limit = 3, windowSeconds = 600)
    @Operation(summary = "Send a fresh OTP — REGISTER while pending, RESET_PASSWORD once active")
    public ApiResponse<Void> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ApiResponse.empty();
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
