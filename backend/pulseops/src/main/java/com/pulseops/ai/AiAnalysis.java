package com.pulseops.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * The AI suggestion attached to an incident. It has its own lifecycle (PENDING → COMPLETED | FAILED) so a slow or
 * failing Gemini call is visible in the UI and retryable, instead of silently leaving the incident half-filled.
 */
@Entity
@Table(name = "ai_analyses")
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, unique = true, updatable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    private Integer confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_actions", columnDefinition = "jsonb")
    private List<String> suggestedActions;

    private String model;

    @Column(columnDefinition = "text")
    private String error;

    @Column(nullable = false)
    private int attempts;

    @Column(columnDefinition = "text")
    private String postmortem;

    @Enumerated(EnumType.STRING)
    @Column(name = "postmortem_status")
    private PostmortemStatus postmortemStatus;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AiAnalysis() {
    }

    public AiAnalysis(Long incidentId, AnalysisStatus status, String model) {
        this.incidentId = incidentId;
        this.status = status;
        this.model = model;
        if (status == AnalysisStatus.DISABLED) {
            this.error = "No GEMINI_API_KEY configured";
        }
    }

    public void markPending() {
        status = AnalysisStatus.PENDING;
        error = null;
        touch();
    }

    public void complete(String rootCause, int confidence, List<String> suggestedActions, String model, int attempts) {
        this.status = AnalysisStatus.COMPLETED;
        this.rootCause = rootCause;
        this.confidence = Math.max(0, Math.min(100, confidence));
        this.suggestedActions = suggestedActions;
        this.model = model;
        this.error = null;
        this.attempts += attempts;
        touch();
    }

    public void fail(String error, int attempts) {
        this.status = AnalysisStatus.FAILED;
        this.error = error;
        this.attempts += attempts;
        touch();
    }

    public void markPostmortemPending() {
        postmortemStatus = PostmortemStatus.PENDING;
        touch();
    }

    public void completePostmortem(String text) {
        postmortem = text;
        postmortemStatus = PostmortemStatus.COMPLETED;
        touch();
    }

    public void failPostmortem(String error) {
        postmortemStatus = PostmortemStatus.FAILED;
        this.error = error;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getIncidentId() { return incidentId; }
    public AnalysisStatus getStatus() { return status; }
    public String getRootCause() { return rootCause; }
    public Integer getConfidence() { return confidence; }
    public List<String> getSuggestedActions() { return suggestedActions; }
    public String getModel() { return model; }
    public String getError() { return error; }
    public int getAttempts() { return attempts; }
    public String getPostmortem() { return postmortem; }
    public PostmortemStatus getPostmortemStatus() { return postmortemStatus; }
    public Instant getUpdatedAt() { return updatedAt; }
}
