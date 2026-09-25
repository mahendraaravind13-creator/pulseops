package com.pulseops.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.common.ProblemResponses;
import com.pulseops.tenant.ApiKeys;
import com.pulseops.tenant.TenantAuth;
import com.pulseops.tenant.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates agents on the ingest path with the {@code X-API-Key} header.
 * The raw key is hashed and looked up through {@link TenantService} (Redis first, Postgres on a miss).
 * The resulting {@link TenantAuth} becomes the principal, so the tenant id is never taken from the payload.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final TenantService tenantService;
    private final ObjectMapper mapper;

    public ApiKeyAuthFilter(TenantService tenantService, ObjectMapper mapper) {
        this.tenantService = tenantService;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            ProblemResponses.write(mapper, request, response, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header");
            return;
        }
        TenantAuth tenant = tenantService.findByApiKeyHash(ApiKeys.hash(rawKey.trim()));
        if (tenant == null) {
            ProblemResponses.write(mapper, request, response, HttpStatus.UNAUTHORIZED, "Invalid API key");
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(tenant, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        MDC.put("tenantId", String.valueOf(tenant.tenantId()));
        chain.doFilter(request, response);
    }
}
