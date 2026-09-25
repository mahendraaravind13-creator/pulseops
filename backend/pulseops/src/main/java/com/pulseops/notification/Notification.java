package com.pulseops.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** In-app notification. Shared by all users of a tenant (read state is per tenant, a deliberate simplification). */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "incident_id", updatable = false)
    private Long incidentId;

    @Column(nullable = false, updatable = false)
    private String type;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(columnDefinition = "text", updatable = false)
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(Long tenantId, Long incidentId, String type, String title, String body) {
        this.tenantId = tenantId;
        this.incidentId = incidentId;
        this.type = type;
        this.title = title;
        this.body = body;
    }

    public void markRead() {
        this.read = true;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getIncidentId() { return incidentId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
}
