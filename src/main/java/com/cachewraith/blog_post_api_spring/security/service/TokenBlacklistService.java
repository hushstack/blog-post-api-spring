package com.cachewraith.blog_post_api_spring.security.service;

import com.cachewraith.blog_post_api_spring.common.constant.AppConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Revocation state for JWTs, in Redis.
 *
 * <p>Access tokens are stateless, so logout cannot invalidate one directly; instead the {@code jti}
 * is blacklisted until the token would have expired anyway, which bounds the memory cost.
 *
 * <p>Refresh tokens live in a per-user hash at {@code refresh:{userId}}, field {@code jti}, value
 * expiry-epoch. The spec sketches {@code refresh:{userId}:{jti}} as separate keys, but that shape
 * forces a {@code KEYS refresh:{userId}:*} scan to end every session at once — an O(keyspace)
 * command that blocks the whole Redis instance. A hash makes "revoke one device" an HDEL and
 * "revoke all" a DEL, both O(1)-ish, while keeping multi-device tracking (OWASP A07).
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redis;

    public void blacklist(String jti, Instant tokenExpiry) {
        Duration ttl = Duration.between(Instant.now(), tokenExpiry);
        if (ttl.isNegative() || ttl.isZero()) {
            return; // already expired; nothing to revoke
        }
        redis.opsForValue().set(AppConstants.KEY_BLACKLIST + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(AppConstants.KEY_BLACKLIST + jti));
    }

    public void registerRefresh(UUID userId, String jti, Instant expiry) {
        Duration ttl = Duration.between(Instant.now(), expiry);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        String key = refreshKey(userId);
        redis.opsForHash().put(key, jti, Long.toString(expiry.getEpochSecond()));
        // Bump the whole hash to outlive its longest-lived member.
        Long current = redis.getExpire(key);
        if (current == null || current < ttl.toSeconds()) {
            redis.expire(key, ttl);
        }
    }

    public boolean isRefreshActive(UUID userId, String jti) {
        Object raw = redis.opsForHash().get(refreshKey(userId), jti);
        if (raw == null) {
            return false;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(raw.toString())).isAfter(Instant.now());
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public void revokeRefresh(UUID userId, String jti) {
        redis.opsForHash().delete(refreshKey(userId), jti);
    }

    /** Ends every session for the user — used after a password reset. */
    public void revokeAllRefresh(UUID userId) {
        redis.delete(refreshKey(userId));
    }

    private String refreshKey(UUID userId) {
        return AppConstants.KEY_REFRESH + userId;
    }
}
