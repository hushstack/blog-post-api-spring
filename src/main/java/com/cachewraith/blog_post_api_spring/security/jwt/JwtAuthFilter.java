package com.cachewraith.blog_post_api_spring.security.jwt;

import com.cachewraith.blog_post_api_spring.security.service.TokenBlacklistService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import com.cachewraith.blog_post_api_spring.security.userdetails.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the bearer token on each request.
 *
 * <p>A token is accepted only when it verifies, is of type ACCESS, and its {@code jti} is not
 * blacklisted — so a refresh token cannot be replayed as an access token, and logout takes effect
 * immediately (OWASP A07).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final TokenBlacklistService blacklist;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtProvider.parse(token);
            if (jwtProvider.typeOf(claims) != TokenType.ACCESS || blacklist.isBlacklisted(claims.getId())) {
                chain.doFilter(request, response);
                return;
            }

            AppUserPrincipal principal =
                    userDetailsService.loadUserById(UUID.fromString(claims.getSubject()));
            if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
                chain.doFilter(request, response);
                return;
            }

            var authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException ex) {
            // Unauthenticated request; the entry point turns this into a 401. The token itself is
            // never logged (OWASP A09).
            log.debug("Rejected bearer token: {}", ex.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String value = header.substring(PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
