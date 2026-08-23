package com.cachewraith.blog_post_api_spring.modules.auth.service;

import com.cachewraith.blog_post_api_spring.modules.auth.entity.OtpPurpose;
import java.util.UUID;

public interface OtpService {

    /** Generates a code, stores its hash in Redis, and queues delivery. */
    void issue(UUID userId, String email, OtpPurpose purpose);

    /** Consumes the code on success; throws on a bad code or too many attempts. */
    void verify(UUID userId, String code, OtpPurpose purpose);
}
