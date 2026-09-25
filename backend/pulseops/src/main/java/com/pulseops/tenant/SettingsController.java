package com.pulseops.tenant;

import com.pulseops.auth.AuthenticatedUser;
import com.pulseops.config.PulseOpsProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final TenantService tenantService;
    private final PulseOpsProperties properties;
    private final JdbcTemplate jdbc;

    public SettingsController(TenantService tenantService, PulseOpsProperties properties, JdbcTemplate jdbc) {
        this.tenantService = tenantService;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    public record TenantInfo(Long id, String name, String slug, Instant createdAt) {
    }

    public record Settings(TenantInfo tenant, String apiKeyPrefix, int rateLimitPerMinute, int retentionDays,
                           String webhookUrl) {
    }

    public record RotatedKeyResponse(String apiKey, String apiKeyPrefix) {
    }

    public record WebhookRequest(
            @Size(max = 500)
            @Pattern(regexp = "^$|^https?://.+", message = "must be an http(s) URL")
            String webhookUrl) {
    }

    public record DeliveryView(Long id, String event, String title, String status, int attempts, Integer responseCode,
                               String lastError, Instant createdAt, Instant deliveredAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Settings get(@AuthenticationPrincipal AuthenticatedUser user) {
        return toSettings(tenantService.get(user.tenantId()));
    }

    @PostMapping("/api-key/rotate")
    @PreAuthorize("hasRole('OWNER')")
    public RotatedKeyResponse rotate(@AuthenticationPrincipal AuthenticatedUser user) {
        TenantService.RotatedKey key = tenantService.rotateApiKey(user.tenantId());
        return new RotatedKeyResponse(key.apiKey(), key.apiKeyPrefix());
    }

    @PutMapping("/webhook")
    @PreAuthorize("hasRole('OWNER')")
    public Settings updateWebhook(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody WebhookRequest request) {
        return toSettings(tenantService.updateWebhook(user.tenantId(), request.webhookUrl()));
    }

    @GetMapping("/webhook/deliveries")
    public List<DeliveryView> deliveries(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(defaultValue = "20") int limit) {
        return jdbc.query("""
                SELECT id, event, title, status, attempts, response_code, last_error, created_at, delivered_at
                  FROM webhook_deliveries
                 WHERE tenant_id = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """, (rs, n) -> new DeliveryView(rs.getLong("id"), rs.getString("event"), rs.getString("title"),
                rs.getString("status"), rs.getInt("attempts"), (Integer) rs.getObject("response_code"),
                rs.getString("last_error"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("delivered_at") == null ? null : rs.getTimestamp("delivered_at").toInstant()),
                user.tenantId(), Math.max(1, Math.min(limit, 100)));
    }

    private Settings toSettings(Tenant t) {
        return new Settings(new TenantInfo(t.getId(), t.getName(), t.getSlug(), t.getCreatedAt()), t.getApiKeyPrefix(),
                t.getRateLimitPerMinute(), properties.monitoring().retentionDays(), t.getWebhookUrl());
    }
}
