package com.pulseops.auth;

import com.pulseops.config.PulseOpsProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies HS256-signed JWTs.
 *
 * <p>Claims: {@code sub} = user id, {@code tenantId}, {@code role}, {@code email}. Everything needed to authorize a
 * request is inside the token, so verification is a signature check plus an expiry check, with no database call.
 * The trade-off: a token cannot be revoked before it expires. A 12-hour lifetime keeps that window bounded.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(PulseOpsProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.jwt().ttl();
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("tenantId", user.getTenantId())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("tenantId", Long.class),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
