package com.cachewraith.blog_post_api_spring.modules.auth.service.impl;

import com.cachewraith.blog_post_api_spring.common.constant.AppConstants;
import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ForgotPasswordRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.LoginRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.RefreshRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.RegisterRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.ResetPasswordRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.request.VerifyOtpRequest;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.RegisterResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response.TokenPairResponse;
import com.cachewraith.blog_post_api_spring.modules.auth.entity.OtpPurpose;
import com.cachewraith.blog_post_api_spring.modules.auth.service.AuthService;
import com.cachewraith.blog_post_api_spring.modules.auth.service.OtpService;
import com.cachewraith.blog_post_api_spring.modules.user.entity.User;
import com.cachewraith.blog_post_api_spring.modules.user.entity.UserStatus;
import com.cachewraith.blog_post_api_spring.modules.user.repository.UserRepository;
import com.cachewraith.blog_post_api_spring.security.jwt.JwtProvider;
import com.cachewraith.blog_post_api_spring.security.jwt.TokenType;
import com.cachewraith.blog_post_api_spring.security.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Duration RESET_TTL = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtProvider jwtProvider;
    private final TokenBlacklistService tokenBlacklist;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_USED);
        }

        User user =
                userRepository.save(
                        User.builder()
                                .email(request.email().toLowerCase())
                                .username(request.username())
                                .passwordHash(passwordEncoder.encode(request.password()))
                                .fullName(request.fullName())
                                .status(UserStatus.PENDING_VERIFICATION)
                                .build());

        otpService.issue(user.getId(), user.getEmail(), OtpPurpose.REGISTER);

        return new RegisterResponse(
                user.getId(), user.getEmail(), "Verification code sent to your email");
    }

    @Override
    @Transactional
    public TokenPairResponse verifyOtp(VerifyOtpRequest request) {
        User user =
                userRepository
                        .findByEmailIgnoreCase(request.email())
                        .orElseThrow(() -> new BusinessException(ErrorCode.OTP_INVALID));

        otpService.verify(user.getId(), request.code(), OtpPurpose.REGISTER);

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }
        return issueTokens(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public TokenPairResponse login(LoginRequest request) {
        // One generic failure for "no such user" and "wrong password" alike, so login cannot be
        // used to enumerate accounts (OWASP A07).
        User user =
                userRepository
                        .findByEmailIgnoreCase(request.email())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        return issueTokens(user.getId());
    }

    @Override
    public TokenPairResponse refresh(RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtProvider.parse(request.refreshToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        if (jwtProvider.typeOf(claims) != TokenType.REFRESH) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String jti = claims.getId();

        // The token must still be one of this user's registered sessions. Rotation below revokes
        // the presented token, so a stolen refresh token is usable at most once (OWASP A07).
        if (!tokenBlacklist.isRefreshActive(userId, jti)) {
            throw new BusinessException(ErrorCode.TOKEN_REVOKED);
        }
        tokenBlacklist.revokeRefresh(userId, jti);

        return issueTokens(userId);
    }

    @Override
    public void logout(String bearerToken) {
        Claims claims;
        try {
            claims = jwtProvider.parse(bearerToken);
        } catch (JwtException | IllegalArgumentException ex) {
            return; // nothing usable to revoke
        }
        tokenBlacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(request.email());
        // Always succeeds from the caller's point of view; a differing response would disclose
        // which addresses are registered (OWASP A07).
        maybeUser.ifPresent(
                user -> {
                    String token = generateResetToken();
                    redis.opsForValue()
                            .set(AppConstants.KEY_RESET + token, user.getId().toString(), RESET_TTL);
                    otpService.issue(user.getId(), user.getEmail(), OtpPurpose.RESET_PASSWORD);
                    log.info("Password reset requested for user {}", user.getId());
                });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String key = AppConstants.KEY_RESET + request.token();
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }

        User user =
                userRepository
                        .findById(UUID.fromString(userId))
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Single-use token, and every existing session dies with the old password.
        redis.delete(key);
        tokenBlacklist.revokeAllRefresh(user.getId());
    }

    private TokenPairResponse issueTokens(UUID userId) {
        JwtProvider.IssuedToken access = jwtProvider.issue(userId, TokenType.ACCESS);
        JwtProvider.IssuedToken refresh = jwtProvider.issue(userId, TokenType.REFRESH);
        tokenBlacklist.registerRefresh(userId, refresh.jti(), refresh.expiresAt());
        return TokenPairResponse.of(access.value(), refresh.value(), access.expiresAt());
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
