package com.pulseops.tenant;

/**
 * The small, cache-friendly view of a tenant needed to authenticate an ingest request.
 * This is what lives in Redis under {@code apiKeys::<sha256>}.
 */
public record TenantAuth(Long tenantId, String slug, int rateLimitPerMinute) {
}
