package com.cachewraith.blog_post_api_spring.modules.auth.service.impl;

import com.cachewraith.blog_post_api_spring.common.constant.AppConstants;
import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import com.cachewraith.blog_post_api_spring.messaging.event.OtpSendEvent;
import com.cachewraith.blog_post_api_spring.messaging.producer.EventPublisher;
import com.cachewraith.blog_post_api_spring.modules.auth.entity.OtpPurpose;
import com.cachewraith.blog_post_api_spring.modules.auth.entity.OtpVerification;
import com.cachewraith.blog_post_api_spring.modules.auth.repository.OtpVerificationRepository;
import com.cachewraith.blog_post_api_spring.modules.auth.service.OtpService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OTP issue/verify.
 *
 * <p>Redis holds a hash of the code, never the code itself, so a Redis read cannot be replayed
 * (OWASP A04). Verification is capped at {@code OTP_MAX_ATTEMPTS} per window, which is what makes a
 * 6-digit secret defensible at all — without the cap it is a 10^6 space open to brute force
 * (OWASP A07).
 *
 * <p>Issue is throttled per account and purpose by {@code RESEND_COOLDOWN}: however many clients
 * ask, one mailbox receives at most one code per window. The per-client cap on the controller
 * does not cover this — an attacker with many addresses would otherwise be able to flood a single
 * victim's inbox (OWASP A06).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final OtpVerificationRepository otpRepository;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public boolean issue(UUID userId, String email, OtpPurpose purpose) {
        // SET NX EX: the first caller in the window wins, every later one is dropped. Dropped, not
        // failed — the caller's response must not change, or it discloses that a code exists for
        // this address and therefore that the account does (OWASP A07).
        Boolean first =
                redis.opsForValue().setIfAbsent(cooldownKey(userId, purpose), "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(first)) {
            log.info("{} OTP for user {} suppressed: resend cooldown active", purpose, userId);
            return false;
        }

        String code = generateCode();
        String hash = passwordEncoder.encode(code);

        redis.opsForValue().set(codeKey(userId, purpose), hash, OTP_TTL);
        redis.delete(attemptsKey(userId, purpose));

        otpRepository.save(
                OtpVerification.builder()
                        .userId(userId)
                        .codeHash(hash)
                        .purpose(purpose)
                        .expiresAt(Instant.now().plus(OTP_TTL))
                        .consumed(false)
                        .build());

        eventPublisher.sendOtp(
                new OtpSendEvent(
                        userId, email, code, purpose.name(), (int) OTP_TTL.toMinutes()));
        return true;
    }

    @Override
    public void verify(UUID userId, String code, OtpPurpose purpose) {
        String attemptsKey = attemptsKey(userId, purpose);
        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redis.expire(attemptsKey, OTP_TTL);
        }
        if (attempts != null && attempts > AppConstants.OTP_MAX_ATTEMPTS) {
            redis.delete(codeKey(userId, purpose));
            throw new BusinessException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        }

        String stored = redis.opsForValue().get(codeKey(userId, purpose));
        if (stored == null || !passwordEncoder.matches(code, stored)) {
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }

        redis.delete(codeKey(userId, purpose));
        redis.delete(attemptsKey);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, AppConstants.OTP_LENGTH);
        return String.format("%0" + AppConstants.OTP_LENGTH + "d", RANDOM.nextInt(bound));
    }

    private String codeKey(UUID userId, OtpPurpose purpose) {
        return AppConstants.KEY_OTP + purpose.name().toLowerCase() + ":" + userId;
    }

    private String attemptsKey(UUID userId, OtpPurpose purpose) {
        return AppConstants.KEY_OTP_ATTEMPTS + purpose.name().toLowerCase() + ":" + userId;
    }

    private String cooldownKey(UUID userId, OtpPurpose purpose) {
        return AppConstants.KEY_OTP_COOLDOWN + purpose.name().toLowerCase() + ":" + userId;
    }
}
