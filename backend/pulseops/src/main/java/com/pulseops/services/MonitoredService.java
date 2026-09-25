package com.pulseops.services;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A service that reports metrics. Rows are created by the evaluator's upsert (see SampleStore), never by the UI,
 * so this entity is read-only from JPA's point of view.
 */
@Entity
@Table(name = "services")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    private String hostname;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MonitoredService() {
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getHostname() { return hostname; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
}
