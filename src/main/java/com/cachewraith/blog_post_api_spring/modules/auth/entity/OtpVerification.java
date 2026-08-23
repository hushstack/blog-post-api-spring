package com.cachewraith.blog_post_api_spring.modules.auth.entity;

import com.cachewraith.blog_post_api_spring.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit trail only. The verifiable code lives in Redis under {@code otp:{userId}}; what is stored
 * here is a hash, so a database read cannot replay a code (OWASP A04).
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "otp_verifications",
        indexes = @Index(name = "ix_otp_user", columnList = "user_id"))
public class OtpVerification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean consumed = false;
}
