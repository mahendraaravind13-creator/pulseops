package com.pulseops.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "api_key_hash", nullable = false, unique = true)
    private String apiKeyHash;

    @Column(name = "api_key_prefix", nullable = false)
    private String apiKeyPrefix;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Tenant() {
    }

    public Tenant(String name, String slug, ApiKeys.Generated apiKey, int rateLimitPerMinute) {
        this.name = name;
        this.slug = slug;
        this.rateLimitPerMinute = rateLimitPerMinute;
        replaceApiKey(apiKey);
    }

    public void replaceApiKey(ApiKeys.Generated apiKey) {
        this.apiKeyHash = apiKey.hash();
        this.apiKeyPrefix = apiKey.prefix();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getApiKeyHash() { return apiKeyHash; }
    public String getApiKeyPrefix() { return apiKeyPrefix; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
