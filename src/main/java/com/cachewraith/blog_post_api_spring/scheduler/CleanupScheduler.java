package com.cachewraith.blog_post_api_spring.scheduler;

import com.cachewraith.blog_post_api_spring.modules.auth.repository.OtpVerificationRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges spent OTP audit rows.
 *
 * <p>The verifiable codes live in Redis and expire on their own; what accumulates in Postgres is
 * the {@code otp_verifications} audit trail, which nothing else ever deletes. Left alone it grows
 * once per registration and password reset, forever.
 *
 * <p>Rows are kept for a retention window rather than dropped the moment they expire, so a recent
 * "was a reset code ever issued for this account?" question is still answerable (OWASP A09).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final OtpVerificationRepository otpVerificationRepository;

    @Value("${app.cleanup.otp-retention-days:30}")
    private int otpRetentionDays;

    /**
     * Runs nightly at 03:15. Every instance of a scaled deployment will run this: the delete is
     * idempotent, so the redundancy is harmless, but introduce a lock (ShedLock or equivalent)
     * before adding a job where it would not be.
     */
    @Scheduled(cron = "${app.cleanup.otp-cron:0 15 3 * * *}")
    @Transactional
    public void purgeExpiredOtpVerifications() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(otpRetentionDays));
        int removed = otpVerificationRepository.deleteExpiredBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} OTP audit row(s) expired before {}", removed, cutoff);
        }
    }
}
