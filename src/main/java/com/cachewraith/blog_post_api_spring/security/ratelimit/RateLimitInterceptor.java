package com.cachewraith.blog_post_api_spring.security.ratelimit;

import com.cachewraith.blog_post_api_spring.common.annotation.RateLimited;
import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@link RateLimited} on annotated handlers.
 *
 * <p>An interceptor rather than an AOP aspect: the limit is a property of the HTTP request, and
 * preHandle rejects before argument binding or any service call runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimited limit = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (limit == null) {
            return true;
        }

        String scope = limit.key().isBlank() ? request.getRequestURI() : limit.key();
        String bucket = scope + ":" + callerId(request);

        if (!rateLimitService.tryConsume(bucket, limit.limit(), Duration.ofSeconds(limit.windowSeconds()))) {
            // Logged so repeated throttling is visible as the attack signal it usually is
            // (OWASP A09). The caller id is an IP or user id, never a credential.
            log.warn("Rate limit exceeded for {} on {}", callerId(request), scope);
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        return true;
    }

    /**
     * Authenticated callers are limited per user, so rotating IPs does not widen the budget.
     * Anonymous ones fall back to the socket address.
     *
     * <p>{@code getRemoteAddr()} deliberately, not {@code X-Forwarded-For}: a client can set that
     * header freely, and trusting it would let an attacker mint a fresh budget per request. Behind
     * a proxy, configure {@code server.forward-headers-strategy} so the container resolves the
     * real address before it reaches here.
     */
    private String callerId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return "user:" + principal.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
