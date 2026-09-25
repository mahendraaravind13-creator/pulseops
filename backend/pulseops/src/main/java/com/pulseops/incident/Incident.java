package com.pulseops.incident;

import com.pulseops.common.ConflictException;
import com.pulseops.rules.Metric;
import com.pulseops.rules.Operator;
import com.pulseops.rules.RuleCondition;
import com.pulseops.rules.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

/**
 * An incident and its state machine.
 *
 * <p>Rows are inserted by {@link IncidentService#openOrRecordBreach} with plain SQL (INSERT ... ON CONFLICT), so this
 * entity never inserts. JPA is used for the status transitions, where {@link Version} gives optimistic locking.
 *
 * <p>The telemetry counters (lastBreachAt, peakValue, breachCount) are marked read-only for JPA and updated with an
 * atomic SQL increment instead. That way a breaching sample arriving every few seconds does not bump the version,
 * and an engineer pressing "Acknowledge" is not rejected with a conflict just because new samples came in.
 * {@link DynamicUpdate} makes Hibernate write only the columns that changed.
 */
@Entity
@Table(name = "incidents")
@DynamicUpdate
public class Incident {

    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private Long serviceId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", nullable = false, updatable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Metric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Operator operator;

    @Column(nullable = false, updatable = false)
    private double threshold;

    @Column(name = "duration_seconds", nullable = false, updatable = false)
    private int durationSeconds;

    @Column(nullable = false, updatable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "last_breach_at", insertable = false, updatable = false)
    private Instant lastBreachAt;

    @Column(name = "peak_value", insertable = false, updatable = false)
    private double peakValue;

    @Column(name = "breach_count", insertable = false, updatable = false)
    private int breachCount;

    @Version
    private long version;

    protected Incident() {
    }

    public void acknowledge(Long userId, Instant at) {
        if (status != IncidentStatus.OPEN) {
            throw new ConflictException("Only an OPEN incident can be acknowledged (current: " + status + ")");
        }
        status = IncidentStatus.ACKNOWLEDGED;
        acknowledgedAt = at;
        acknowledgedBy = userId;
    }

    public void resolve(Long userId, String note, Instant at) {
        if (status == IncidentStatus.RESOLVED) {
            throw new ConflictException("Incident is already resolved");
        }
        status = IncidentStatus.RESOLVED;
        resolvedAt = at;
        resolvedBy = userId;
        resolutionNote = note;
    }

    public void autoResolve(Instant at) {
        resolve(null, null, at);
    }

    public boolean isActive() {
        return status != IncidentStatus.RESOLVED;
    }

    public RuleCondition condition() {
        return new RuleCondition(metric, operator, threshold, durationSeconds);
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getServiceId() { return serviceId; }
    public Long getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public Metric getMetric() { return metric; }
    public Operator getOperator() { return operator; }
    public double getThreshold() { return threshold; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getTitle() { return title; }
    public Severity getSeverity() { return severity; }
    public IncidentStatus getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Long getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getLastBreachAt() { return lastBreachAt; }
    public double getPeakValue() { return peakValue; }
    public int getBreachCount() { return breachCount; }
    public long getVersion() { return version; }

    /** Test helper: builds an in-memory incident without the database. */
    static Incident forTest(IncidentStatus status) {
        Incident incident = new Incident();
        incident.status = status;
        return incident;
    }
}
