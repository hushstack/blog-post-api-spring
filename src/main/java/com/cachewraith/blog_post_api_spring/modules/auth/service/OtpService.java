package com.cachewraith.blog_post_api_spring.modules.auth.service;

import com.cachewraith.blog_post_api_spring.modules.auth.entity.OtpPurpose;
import java.util.UUID;

public interface OtpService {

    /**
     * Generates a code, stores its hash in Redis, and queues delivery. Returns {@code false} when
     * a code for this user and purpose was issued within the resend cooldown, in which case
     * nothing is sent; callers must not surface that distinction to an anonymous client.
     */
    boolean issue(UUID userId, String email, OtpPurpose purpose);

    /** Consumes the code on success; throws on a bad code or too many attempts. */
    void verify(UUID userId, String code, OtpPurpose purpose);
}
