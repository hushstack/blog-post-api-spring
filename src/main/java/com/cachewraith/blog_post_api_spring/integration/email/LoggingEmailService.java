package com.cachewraith.blog_post_api_spring.integration.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback used when no SMTP host is configured, so the app still boots and registration still
 * completes end to end locally.
 *
 * <p>It delivers nothing. The warning is deliberately loud, because an environment running on this
 * implementation cannot verify a single account by mail.
 */
@Slf4j
@RequiredArgsConstructor
public class LoggingEmailService implements EmailService {

    private final EmailProperties properties;

    @Override
    public void sendOtp(String recipient, String code, String purpose) {
        log.warn("No mail host configured — {} OTP for {} was NOT delivered", purpose, recipient);
        if (properties.isLogCodes()) {
            // Guarded by app.email.log-codes and emitted at DEBUG: a code in a log is a code
            // anyone with log access can use, so this is a local-development affordance only
            // (OWASP A09).
            log.debug("Local-only {} OTP for {}: {}", purpose, recipient, code);
        }
    }
}
