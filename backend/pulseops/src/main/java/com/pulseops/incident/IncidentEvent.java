package com.pulseops.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One line of an incident's timeline. Append-only: events are never updated, which makes the timeline an audit log. */
@Entity
@Table(name = "incident_events")
public class IncidentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private IncidentEventType type;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String message;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IncidentEvent() {
    }

    public IncidentEvent(Long incidentId, IncidentEventType type, String message, Long actorUserId, Instant createdAt) {
        this.incidentId = incidentId;
        this.type = type;
        this.message = message;
        this.actorUserId = actorUserId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getIncidentId() { return incidentId; }
    public IncidentEventType getType() { return type; }
    public String getMessage() { return message; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
