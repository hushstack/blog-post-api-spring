package com.cachewraith.blog_post_api_spring.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Caps how often one caller may invoke a handler.
 *
 * <p>Unauthenticated endpoints — login, register, OTP, password reset — are otherwise free to brute
 * force at line rate: the account lockout in {@code OtpService} bounds guesses per user, but
 * nothing bounds attempts per client across accounts (OWASP A06, A07).
 *
 * <p>Enforced by {@code RateLimitInterceptor}. A handler without this annotation is not limited.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /** Requests allowed per window. */
    int limit() default 20;

    /** Window length in seconds. */
    int windowSeconds() default 60;

    /**
     * Distinguishes counters when two handlers should not share a budget. Defaults to the request
     * path, which is usually what you want.
     */
    String key() default "";
}
