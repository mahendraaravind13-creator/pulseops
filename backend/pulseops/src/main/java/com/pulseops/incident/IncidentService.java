package com.pulseops.incident;

import com.pulseops.ai.AiAnalysis;
import com.pulseops.ai.AiAnalysisRepository;
import com.pulseops.ai.AnalysisStatus;
import com.pulseops.auth.AuthenticatedUser;
import com.pulseops.common.ConflictException;
import com.pulseops.common.NotFoundException;
import com.pulseops.config.PulseOpsProperties;
import com.pulseops.incident.IncidentEvents.AnalysisRequested;
import com.pulseops.incident.IncidentEvents.IncidentOpened;
import com.pulseops.incident.IncidentEvents.IncidentResolved;
import com.pulseops.incident.IncidentEvents.PostmortemRequested;
import com.pulseops.rules.AlertRule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Owns every change to an incident. Two kinds of callers:
 * <ul>
 *   <li>The evaluator (Kafka consumer thread): opens incidents, records further breaches, auto-resolves.</li>
 *   <li>Engineers (HTTP): acknowledge, resolve, add notes, retry AI.</li>
 * </ul>
 * Both can act on the same incident at the same moment, possibly on different app instances. Correctness comes from
 * the database, not from locks in Java: a partial unique index for "only one active incident per rule and service",
 * and an optimistic @Version for status transitions.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidents;
    private final IncidentEventRepository events;
    private final AiAnalysisRepository analyses;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;
    private final PulseOpsProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, IncidentEventRepository events, AiAnalysisRepository analyses,
                           JdbcTemplate jdbc, ApplicationEventPublisher publisher, PulseOpsProperties properties,
                           MeterRegistry meters, Clock clock) {
        this.incidents = incidents;
        this.events = events;
        this.analyses = analyses;
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- evaluator side

    /**
     * Opens an incident for (service, rule), or, if one is already active, records one more breach on it.
     *
     * <p>The INSERT names the partial unique index as its conflict target. If another consumer (or another instance)
     * opened the incident first, the INSERT does nothing and returns no row; we then update the existing one. This
     * is a single atomic statement, so there is no "check, then insert" race and no exception that would abort
     * the surrounding transaction.
     *
     * @return the id of a newly opened incident, or empty if the breach was attached to an existing one
     */
    @Transactional
    public Optional<Long> openOrRecordBreach(AlertRule rule, long serviceId, String serviceName, double value, Instant at) {
        String title = rule.condition().describe() + " on " + serviceName;
        List<Long> inserted = jdbc.queryForList("""
                INSERT INTO incidents (tenant_id, service_id, rule_id, rule_name, metric, operator, threshold,
                                       duration_seconds, title, severity, status, opened_at, last_breach_at,
                                       peak_value, breach_count, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, 1, 0)
                ON CONFLICT (service_id, rule_id) WHERE status <> 'RESOLVED' DO NOTHING
                RETURNING id
                """, Long.class,
                rule.getTenantId(), serviceId, rule.getId(), rule.getName(), rule.getMetric().name(),
                rule.getOperator().name(), rule.getThreshold(), rule.getDurationSeconds(), truncate(title, 200),
                rule.getSeverity().name(), Timestamp.from(at), Timestamp.from(at), value);

        if (inserted.isEmpty()) {
            jdbc.update("""
                    UPDATE incidents
                       SET last_breach_at = GREATEST(last_breach_at, ?),
                           breach_count   = breach_count + 1,
                           peak_value     = CASE WHEN operator = 'GT' THEN GREATEST(peak_value, ?)
                                                 ELSE LEAST(peak_value, ?) END
                     WHERE service_id = ? AND rule_id = ? AND status <> 'RESOLVED'
                    """, Timestamp.from(at), value, value, serviceId, rule.getId());
            return Optional.empty();
        }

        Long incidentId = inserted.getFirst();
        events.save(new IncidentEvent(incidentId, IncidentEventType.OPENED,
                "Opened by rule \"" + rule.getName() + "\": " + rule.getMetric().label() + " was "
                        + format(value) + rule.getMetric().unit(), null, at));
        boolean aiEnabled = properties.gemini().enabled();
        analyses.save(new AiAnalysis(incidentId, aiEnabled ? AnalysisStatus.PENDING : AnalysisStatus.DISABLED,
                properties.gemini().model()));
        meters.counter("pulseops.incidents.opened", "severity", rule.getSeverity().name()).increment();
        publisher.publishEvent(new IncidentOpened(incidentId, rule.getTenantId()));
        return Optional.of(incidentId);
    }

    /** Called when a rule's condition has been healthy for its whole window. */
    @Transactional
    public void autoResolveIfActive(Long ruleId, Long serviceId, Instant at) {
        incidents.findActive(serviceId, ruleId).ifPresent(incident -> {
            incident.autoResolve(at);
            events.save(new IncidentEvent(incident.getId(), IncidentEventType.AUTO_RESOLVED,
                    "Condition cleared for the rule's full window. Resolved automatically.", null, at));
            publisher.publishEvent(new IncidentResolved(incident.getId(), incident.getTenantId(), true));
        });
    }

    @Transactional
    public void resolveAllForDeletedRule(AlertRule rule) {
        Instant now = clock.instant();
        for (Incident incident : incidents.findActiveForRule(rule.getId())) {
            incident.resolve(null, "Rule \"" + rule.getName() + "\" was deleted", now);
            events.save(new IncidentEvent(incident.getId(), IncidentEventType.RESOLVED,
                    "Resolved because its alert rule was deleted", null, now));
            publisher.publishEvent(new IncidentResolved(incident.getId(), incident.getTenantId(), true));
        }
    }

    // ---------------------------------------------------------------- engineer side

    @Transactional
    public void acknowledge(AuthenticatedUser user, Long id, long expectedVersion) {
        Incident incident = loadForUpdate(user.tenantId(), id, expectedVersion);
        Instant now = clock.instant();
        incident.acknowledge(user.userId(), now);
        events.save(new IncidentEvent(id, IncidentEventType.ACKNOWLEDGED, "Acknowledged", user.userId(), now));
        incidents.flush(); // surface an optimistic-lock conflict here as a 409, not later at commit
    }

    @Transactional
    public void resolve(AuthenticatedUser user, Long id, long expectedVersion, String note) {
        Incident incident = loadForUpdate(user.tenantId(), id, expectedVersion);
        Instant now = clock.instant();
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        incident.resolve(user.userId(), cleanNote, now);
        events.save(new IncidentEvent(id, IncidentEventType.RESOLVED,
                cleanNote == null ? "Resolved" : "Resolved: " + cleanNote, user.userId(), now));
        incidents.flush();
        publisher.publishEvent(new IncidentResolved(id, user.tenantId(), false));
    }

    @Transactional
    public void addNote(AuthenticatedUser user, Long id, String message) {
        requireIncident(user.tenantId(), id);
        events.save(new IncidentEvent(id, IncidentEventType.NOTE, message.trim(), user.userId(), clock.instant()));
    }

    @Transactional
    public void retryAnalysis(Long tenantId, Long id) {
        requireIncident(tenantId, id);
        if (!properties.gemini().enabled()) {
            throw new ConflictException("AI analysis is not configured on this server");
        }
        AiAnalysis analysis = analyses.findByIncidentId(id)
                .orElseGet(() -> analyses.save(new AiAnalysis(id, AnalysisStatus.FAILED, properties.gemini().model())));
        if (analysis.getStatus() == AnalysisStatus.PENDING) {
            throw new ConflictException("Analysis is already running");
        }
        analysis.markPending();
        publisher.publishEvent(new AnalysisRequested(id));
    }

    @Transactional
    public void retryPostmortem(Long tenantId, Long id) {
        Incident incident = requireIncident(tenantId, id);
        if (incident.isActive()) {
            throw new ConflictException("A post-mortem can only be written for a resolved incident");
        }
        if (!properties.gemini().enabled()) {
            throw new ConflictException("AI analysis is not configured on this server");
        }
        AiAnalysis analysis = analyses.findByIncidentId(id)
                .orElseGet(() -> analyses.save(new AiAnalysis(id, AnalysisStatus.FAILED, properties.gemini().model())));
        analysis.markPostmortemPending();
        publisher.publishEvent(new PostmortemRequested(id));
    }

    private Incident loadForUpdate(Long tenantId, Long id, long expectedVersion) {
        Incident incident = requireIncident(tenantId, id);
        if (incident.getVersion() != expectedVersion) {
            throw new ConflictException("Incident was modified by someone else. Reload and try again.");
        }
        return incident;
    }

    private Incident requireIncident(Long tenantId, Long id) {
        return incidents.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Incident", id));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String format(double value) {
        return String.format("%.1f", value);
    }
}
