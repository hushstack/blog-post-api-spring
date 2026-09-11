package com.cachewraith.blog_post_api_spring.modules.auth.dto.v1.response;

import com.cachewraith.blog_post_api_spring.modules.auth.entity.OtpPurpose;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * What {@code /auth/verify-otp} hands back, shaped by the purpose the account's state selected.
 * {@code purpose} tells the client which one it got; fields the purpose does not use are omitted.
 *
 * <ul>
 *   <li>{@code REGISTER} — a token pair, flat, exactly as {@code /auth/login} returns one.
 *   <li>{@code RESET_PASSWORD} — a single-use {@code resetToken} for {@code /auth/reset-password}
 *       and deliberately no session: proving you can read a mailbox must not, on its own, log
 *       anyone in (OWASP A07).
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerifyOtpResponse(
        OtpPurpose purpose,
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        String resetToken,
        Instant resetTokenExpiresAt) {

    public static VerifyOtpResponse registered(TokenPairResponse tokens) {
        return new VerifyOtpResponse(
                OtpPurpose.REGISTER,
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.tokenType(),
                tokens.accessTokenExpiresAt(),
                null,
                null);
    }

    public static VerifyOtpResponse resetAllowed(String resetToken, Instant expiresAt) {
        return new VerifyOtpResponse(
                OtpPurpose.RESET_PASSWORD, null, null, null, null, resetToken, expiresAt);
    }
}
