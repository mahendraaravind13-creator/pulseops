package com.pulseops.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    /** Null means the rule applies to every service of the tenant. */
    @Column(name = "service_id")
    private Long serviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Metric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operator operator;

    @Column(nullable = false)
    private double threshold;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AlertRule() {
    }

    public AlertRule(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void update(String name, Long serviceId, Metric metric, Operator operator, double threshold,
                       int durationSeconds, Severity severity, boolean enabled) {
        this.name = name;
        this.serviceId = serviceId;
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.durationSeconds = durationSeconds;
        this.severity = severity;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public RuleCondition condition() {
        return new RuleCondition(metric, operator, threshold, durationSeconds);
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public Long getServiceId() { return serviceId; }
    public Metric getMetric() { return metric; }
    public Operator getOperator() { return operator; }
    public double getThreshold() { return threshold; }
    public int getDurationSeconds() { return durationSeconds; }
    public Severity getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
