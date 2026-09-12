package com.cachewraith.blog_post_api_spring.security.ratelimit;

import com.cachewraith.blog_post_api_spring.common.constant.AppConstants;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Fixed-window request counter in Redis.
 *
 * <p>Redis rather than an in-process map because the limit must hold across every instance — a
 * per-JVM counter multiplies the real limit by the number of replicas, which is no limit at all.
 *
 * <p>A fixed window admits up to 2x the limit across a window boundary. That is accepted here: the
 * goal is to make brute force impractical, not to meter billing, and a sliding-window log would
 * cost a sorted set per caller for a precision nothing needs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redis;

    /**
     * Counts one request against {@code bucket} and reports whether it is still within the limit.
     *
     * <p>The TTL is set only when the counter is created. Refreshing it on every hit would turn the
     * window into a sliding one that a steady stream of requests could keep alive forever.
     */
    public boolean tryConsume(String bucket, int limit, Duration window) {
        String key = AppConstants.KEY_RATE_LIMIT + bucket;
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }
        } catch (DataAccessException ex) {
            // Redis is unreachable or timed out. Fail open rather than locking every user out of
            // login (OWASP A10). Lettuce reports this by throwing, never by returning null, so the
            // catch is what makes the fail-open real. The exception class only: the key carries a
            // user id or an IP (OWASP A09).
            log.warn("Rate limit check skipped, Redis unavailable: {}", ex.getClass().getSimpleName());
            return true;
        }
        if (count == null) {
            return true;
        }
        return count <= limit;
    }
}
