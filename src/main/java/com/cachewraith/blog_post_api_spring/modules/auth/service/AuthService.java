package com.cachewraith.blog_post_api_spring.modules.auth.service;

import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ForgotPasswordRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.LoginRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.RefreshRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.RegisterRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ResetPasswordRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.VerifyOtpRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.RegisterResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.TokenPairResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    TokenPairResponse verifyOtp(VerifyOtpRequest request);

    TokenPairResponse login(LoginRequest request);

    TokenPairResponse refresh(RefreshRequest request);

    void logout(String bearerToken);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
