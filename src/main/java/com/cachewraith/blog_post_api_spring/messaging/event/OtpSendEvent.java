package com.cachewraith.blog_post_api_spring.messaging.event;

import java.util.UUID;

/**
 * Carries the plaintext code because the consumer has to deliver it. Nothing on this queue may be
 * logged at INFO or above (OWASP A09).
 */
public record OtpSendEvent(UUID userId, String email, String code, String purpose) {}
