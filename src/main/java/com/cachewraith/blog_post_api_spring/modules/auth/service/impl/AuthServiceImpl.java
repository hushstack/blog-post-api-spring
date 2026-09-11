package com.cachewraith.blog_post_api_spring.modules.auth.service.impl;

import com.cachewraith.blog_post_api_spring.common.constant.AppConstants;
import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
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
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
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

    /**
     * One entry point for every OTP. The purpose comes from the account's state via
     * {@link #purposeFor}, the same rule {@link #resendOtp} issues by, so the code that was sent
     * is the code that is checked. A plain switch rather than a strategy per purpose: there are
     * two, and the enum is closed.
     */
    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        // Same OTP_INVALID for an unknown address, a suspended account and a wrong code: a distinct
        // response would turn this into an account-enumeration oracle (OWASP A07).
        User user =
                userRepository
                        .findByEmailIgnoreCase(request.email())
                        .orElseThrow(() -> new BusinessException(ErrorCode.OTP_INVALID));
        OtpPurpose purpose =
                purposeFor(user).orElseThrow(() -> new BusinessException(ErrorCode.OTP_INVALID));

        otpService.verify(user.getId(), request.code(), purpose);

        return switch (purpose) {
            case REGISTER -> {
                user.setStatus(UserStatus.ACTIVE);
                userRepository.save(user);
                yield VerifyOtpResponse.registered(issueTokens(user.getId()));
            }
            case RESET_PASSWORD -> {
                // Minted only now, against a code the caller has proved they hold. No session:
                // the holder still has to set a password before anything logs them in.
                String token = generateResetToken();
                redis.opsForValue()
                        .set(AppConstants.KEY_RESET + token, user.getId().toString(), RESET_TTL);
                yield VerifyOtpResponse.resetAllowed(token, Instant.now().plus(RESET_TTL));
            }
        };
    }

    /**
     * Same rule as {@link #verifyOtp}: the purpose is derived from account state, never taken
     * from the caller. A REGISTER code for an already-active account would let anyone who can
     * read the mailbox mint a session with no password at all, which is what RESET_PASSWORD
     * exists to avoid (OWASP A07).
     */
    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        // Every path out of here returns 200 with the same body — unknown address, suspended,
        // cooldown — so the endpoint cannot be used to enumerate accounts (OWASP A07).
        userRepository
                .findByEmailIgnoreCase(request.email())
                .flatMap(user -> purposeFor(user).map(purpose -> Map.entry(user, purpose)))
                .ifPresent(
                        entry -> {
                            User user = entry.getKey();
                            OtpPurpose purpose = entry.getValue();
                            if (otpService.issue(user.getId(), user.getEmail(), purpose)) {
                                log.info("{} OTP resent for user {}", purpose, user.getId());
                            }
                        });
    }

    /**
     * The one rule that binds account state to OTP purpose. Every issuer (register, forgot-password,
     * resend) and the verifier go through it, so the code that was sent is always the code that
     * is checked.
     */
    private static Optional<OtpPurpose> purposeFor(User user) {
        return switch (user.getStatus()) {
            case PENDING_VERIFICATION -> Optional.of(OtpPurpose.REGISTER);
            case ACTIVE -> Optional.of(OtpPurpose.RESET_PASSWORD);
            case SUSPENDED -> Optional.empty();
        };
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
        // Always succeeds from the caller's point of view; a differing response would disclose
        // which addresses are registered (OWASP A07). Only an ACTIVE account receives a code —
        // the same rule verifyOtp reads the purpose back by, so a pending account cannot be
        // handed a RESET_PASSWORD code that verify would then look up as REGISTER.
        userRepository
                .findByEmailIgnoreCase(request.email())
                .filter(user -> purposeFor(user).filter(OtpPurpose.RESET_PASSWORD::equals).isPresent())
                .ifPresent(
                        user -> {
                            // Only the emailed code leaves the building here. The reset token is
                            // minted in verifyOtp, against a code the caller has proved they hold —
                            // issuing it now would create a live credential nobody receives.
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
