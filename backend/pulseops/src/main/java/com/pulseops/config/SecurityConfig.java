package com.pulseops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.auth.ApiKeyAuthFilter;
import com.pulseops.auth.JwtAuthFilter;
import com.pulseops.auth.JwtService;
import com.pulseops.common.ProblemResponses;
import com.pulseops.ingest.RateLimitFilter;
import com.pulseops.ingest.RateLimiter;
import com.pulseops.tenant.TenantService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Two independent security chains, because there are two kinds of callers:
 * <ol>
 *   <li>{@code /api/v1/ingest/**}: agents with an API key, plus per-tenant rate limiting.</li>
 *   <li>Everything else: browser users with a JWT. Register/login, API docs and health are public.</li>
 * </ol>
 * Both are stateless: no HTTP session, so any instance behind the load balancer can serve any request.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain ingestChain(HttpSecurity http, TenantService tenantService, RateLimiter rateLimiter,
                                    ObjectMapper mapper, MeterRegistry meters) throws Exception {
        return http
                .securityMatcher("/api/v1/ingest/**", "/api/v1/ingest")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("AGENT"))
                .addFilterBefore(new ApiKeyAuthFilter(tenantService, mapper), AnonymousAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimiter, mapper, meters), ApiKeyAuthFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, JwtService jwtService, ObjectMapper mapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // no cookies are used, so CSRF does not apply
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService), AnonymousAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                ProblemResponses.write(mapper, req, res, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) ->
                                ProblemResponses.write(mapper, req, res, HttpStatus.FORBIDDEN, "Access denied")))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(PulseOpsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = properties.cors().allowedOrigins();
        config.setAllowedOrigins(origins == null ? List.of() : origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
