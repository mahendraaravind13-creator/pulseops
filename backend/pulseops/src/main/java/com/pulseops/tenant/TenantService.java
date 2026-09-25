package com.pulseops.tenant;

import com.pulseops.common.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cache-aside for API key lookups.
 *
 * <p>Read path: Spring checks Redis key {@code apiKeys::<hash>}; on a miss it runs the method (one indexed
 * Postgres lookup) and stores the result for the configured TTL. Invalid keys return null and are not cached
 * ({@code unless}), so a typo does not fill Redis with garbage.
 *
 * <p>Invalidation: rotating a key evicts the old hash immediately, so the old key stops working at once instead
 * of living on until the TTL expires. The TTL is only the safety net for changes made outside this service.
 */
@Service
public class TenantService {

    public static final String API_KEY_CACHE = "apiKeys";

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Cacheable(cacheNames = API_KEY_CACHE, key = "#apiKeyHash", unless = "#result == null")
    @Transactional(readOnly = true)
    public TenantAuth findByApiKeyHash(String apiKeyHash) {
        return tenants.findByApiKeyHash(apiKeyHash)
                .map(t -> new TenantAuth(t.getId(), t.getSlug(), t.getRateLimitPerMinute()))
                .orElse(null);
    }

    @CacheEvict(cacheNames = API_KEY_CACHE, key = "#result.oldHash()")
    @Transactional
    public RotatedKey rotateApiKey(Long tenantId) {
        Tenant tenant = get(tenantId);
        String oldHash = tenant.getApiKeyHash();
        ApiKeys.Generated fresh = ApiKeys.generate();
        tenant.replaceApiKey(fresh);
        return new RotatedKey(fresh.rawKey(), fresh.prefix(), oldHash);
    }

    @Transactional(readOnly = true)
    public Tenant get(Long tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant", tenantId));
    }

    @Transactional
    public Tenant updateWebhook(Long tenantId, String webhookUrl) {
        Tenant tenant = get(tenantId);
        tenant.setWebhookUrl(webhookUrl == null || webhookUrl.isBlank() ? null : webhookUrl.trim());
        return tenant;
    }

    public record RotatedKey(String apiKey, String apiKeyPrefix, String oldHash) {
    }
}
