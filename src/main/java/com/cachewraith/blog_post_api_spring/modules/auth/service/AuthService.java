package com.cachewraith.blog_post_api_spring.modules.auth.service;

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

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    /**
     * Verifies the code the account's state calls for: REGISTER while pending, which activates
     * the account and returns a token pair; RESET_PASSWORD once active, which returns the token
     * {@link #resetPassword} spends.
     */
    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    /**
     * Issues a fresh code for whichever purpose the account's state calls for — REGISTER while
     * pending verification, RESET_PASSWORD once active — replacing any outstanding one. Silent
     * when the address is unknown, the account is suspended, or the resend cooldown is active.
     */
    void resendOtp(ResendOtpRequest request);

    TokenPairResponse login(LoginRequest request);

    TokenPairResponse refresh(RefreshRequest request);

    void logout(String bearerToken);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
