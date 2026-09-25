package com.pulseops.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.common.ProblemResponses;
import com.pulseops.tenant.TenantAuth;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Runs after {@code ApiKeyAuthFilter}: limits each tenant to its per-minute quota and answers 429 when exceeded. */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper mapper, MeterRegistry meters) {
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantAuth tenant) {
            RateLimiter.Decision decision = rateLimiter.tryAcquire(tenant.tenantId(), tenant.rateLimitPerMinute());
            if (!decision.allowed()) {
                meters.counter("pulseops.samples.rejected", "reason", "rate_limited").increment();
                response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                ProblemResponses.write(mapper, request, response, HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit of " + tenant.rateLimitPerMinute() + " samples per minute exceeded");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
