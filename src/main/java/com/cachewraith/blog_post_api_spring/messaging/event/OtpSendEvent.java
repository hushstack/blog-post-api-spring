package com.cachewraith.blog_post_api_spring.messaging.event;

import java.util.UUID;

/**
 * Carries the plaintext code because the consumer has to deliver it. Nothing on this queue may be
 * logged at INFO or above (OWASP A09).
 *
 * <p>{@code expiresInMinutes} travels with the code so the mail states the expiry the issuer
 * actually set. A second copy of that number in mail configuration is a copy that can disagree.
 */
public record OtpSendEvent(
        UUID userId, String email, String code, String purpose, int expiresInMinutes) {}
