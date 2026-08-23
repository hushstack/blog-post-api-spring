package com.cachewraith.blog_post_api_spring.modules.user.entity;

import com.cachewraith.blog_post_api_spring.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        indexes = {
            @Index(name = "ix_users_email", columnList = "email", unique = true),
            @Index(name = "ix_users_username", columnList = "username", unique = true)
        })
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    /** BCrypt hash. Never serialized into any DTO (OWASP A04). */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 500)
    private String bio;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;
}
