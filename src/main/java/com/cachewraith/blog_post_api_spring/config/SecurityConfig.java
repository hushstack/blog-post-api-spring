package com.cachewraith.blog_post_api_spring.config;

import com.cachewraith.blog_post_api_spring.common.constant.AppConstants;
import com.cachewraith.blog_post_api_spring.security.handler.AccessDeniedHandlerImpl;
import com.cachewraith.blog_post_api_spring.security.handler.AuthEntryPointImpl;
import com.cachewraith.blog_post_api_spring.security.jwt.JwtAuthFilter;
import com.cachewraith.blog_post_api_spring.security.jwt.JwtProperties;
import com.cachewraith.blog_post_api_spring.security.userdetails.CustomUserDetailsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthEntryPointImpl authEntryPoint;
    private final AccessDeniedHandlerImpl accessDeniedHandler;
    private final CustomUserDetailsService userDetailsService;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String v1 = AppConstants.API_V1;
        return http
                // Stateless bearer-token API: there is no session or cookie for CSRF to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(authEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Unauthenticated entry points.
                                        .requestMatchers(v1 + "/auth/**")
                                        .permitAll()
                                        // Own-profile routes must be listed BEFORE the public
                                        // /users/* rule below: matchers are evaluated in order and
                                        // a single '*' matches one whole segment, so '/users/*'
                                        // would otherwise expose '/users/me' anonymously
                                        // (OWASP A01).
                                        .requestMatchers(v1 + "/users/me", v1 + "/users/me/**")
                                        .authenticated()
                                        // Public reads. Per-post visibility is still enforced in
                                        // the service — permitAll here only means "no token
                                        // required", never "anything goes" (OWASP A01).
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                v1 + "/posts/*",
                                                v1 + "/posts/*/comments",
                                                v1 + "/posts/*/share-link",
                                                v1 + "/users/*",
                                                v1 + "/users/*/posts")
                                        .permitAll()
                                        .requestMatchers("/actuator/health")
                                        .permitAll()
                                        .requestMatchers(
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/v3/api-docs/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** BCrypt, strength 12 — deliberately slow, as password hashing must be (OWASP A04). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
