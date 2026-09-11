package com.cachewraith.blog_post_api_spring.integration.email;

/**
 * Outbound mail behind one seam.
 *
 * <p>Two real implementations exist — SMTP where a mail host is configured, and a no-delivery
 * fallback that keeps the app bootable without one — which is what earns the interface.
 */
public interface EmailService {

    /**
     * Delivers a one-time code. Implementations must never write {@code code} to the log above
     * DEBUG (OWASP A09).
     */
    void sendOtp(String recipient, String code, String purpose, int expiresInMinutes);
}
