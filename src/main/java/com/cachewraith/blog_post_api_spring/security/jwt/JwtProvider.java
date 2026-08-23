package com.cachewraith.blog_post_api_spring.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Issues and verifies JWTs. Every token carries a {@code jti} so logout can revoke it. */
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] raw = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (raw.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least "
                            + MIN_SECRET_BYTES
                            + " bytes for HS256; got "
                            + raw.length);
        }
        this.key = Keys.hmacShaKeyFor(raw);
    }

    public IssuedToken issue(UUID userId, TokenType type) {
        Duration ttl =
                type == TokenType.ACCESS
                        ? properties.getAccessTokenTtl()
                        : properties.getRefreshTokenTtl();
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);

        String token =
                Jwts.builder()
                        .id(jti)
                        .subject(userId.toString())
                        .issuer(properties.getIssuer())
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiry))
                        .claim(CLAIM_TYPE, type.name())
                        .signWith(key)
                        .compact();

        return new IssuedToken(token, jti, expiry);
    }

    /**
     * Verifies signature and expiry. Throws {@link JwtException} on anything malformed — callers
     * translate that into a 401 rather than leaking the parser's message.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isExpired(String token) {
        try {
            parse(token);
            return false;
        } catch (ExpiredJwtException ex) {
            return true;
        }
    }

    public TokenType typeOf(Claims claims) {
        String raw = claims.get(CLAIM_TYPE, String.class);
        return raw == null ? null : TokenType.valueOf(raw);
    }

    public record IssuedToken(String value, String jti, Instant expiresAt) {}
}
