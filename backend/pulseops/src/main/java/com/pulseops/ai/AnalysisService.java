package com.pulseops.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.incident.Incident;
import com.pulseops.incident.IncidentEvent;
import com.pulseops.incident.IncidentEventRepository;
import com.pulseops.incident.IncidentEventType;
import com.pulseops.incident.IncidentRepository;
import com.pulseops.services.MonitoredService;
import com.pulseops.services.MonitoredServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs the two AI tasks (diagnosis on open, post-mortem draft on resolve) on the AI thread pool.
 *
 * <p>Structure of each task: short transaction to read context → Gemini call with NO transaction open → short
 * transaction to store the result. Holding a transaction (and a pooled DB connection) across a multi-second HTTP call
 * would let a slow external API exhaust the connection pool and take the whole app down with it.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final GeminiClient gemini;
    private final AiAnalysisRepository analyses;
    private final IncidentRepository incidents;
    private final IncidentEventRepository events;
    private final MonitoredServiceRepository services;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public AnalysisService(GeminiClient gemini, AiAnalysisRepository analyses, IncidentRepository incidents,
                           IncidentEventRepository events, MonitoredServiceRepository services, JdbcTemplate jdbc,
                           ObjectMapper mapper, PlatformTransactionManager txManager) {
        this.gemini = gemini;
        this.analyses = analyses;
        this.incidents = incidents;
        this.events = events;
        this.services = services;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    record ParsedDiagnosis(String rootCause, int confidence, List<String> actions) {
    }

    // ------------------------------------------------------------------ diagnosis

    public void analyze(Long incidentId) {
        String prompt = tx.execute(status -> buildDiagnosisPrompt(incidentId));
        if (prompt == null) {
            return;
        }
        try {
            GeminiClient.Reply reply = gemini.generate(prompt, true);
            ParsedDiagnosis parsed = parseDiagnosis(reply.text());
            tx.executeWithoutResult(status -> {
                analyses.findByIncidentId(incidentId).ifPresent(a ->
                        a.complete(parsed.rootCause(), parsed.confidence(), parsed.actions(), gemini.model(), reply.attempts()));
                events.save(new IncidentEvent(incidentId, IncidentEventType.ANALYSIS_COMPLETED,
                        "AI suggested a probable cause (" + parsed.confidence() + "% confidence)", null, Instant.now()));
            });
        } catch (RuntimeException e) {
            int attempts = e instanceof GeminiClient.GeminiException ge ? ge.attempts() : 1;
            log.warn("AI analysis failed for incident {}: {}", incidentId, e.getMessage());
            markFailed(incidentId, e.getMessage(), attempts);
        }
    }

    public void markFailed(Long incidentId, String reason, int attempts) {
        tx.executeWithoutResult(status -> {
            analyses.findByIncidentId(incidentId).ifPresent(a -> a.fail(reason, attempts));
            events.save(new IncidentEvent(incidentId, IncidentEventType.ANALYSIS_FAILED,
                    "AI analysis failed: " + reason, null, Instant.now()));
        });
    }

    private String buildDiagnosisPrompt(Long incidentId) {
        Incident incident = incidents.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }
        MonitoredService service = services.findById(incident.getServiceId()).orElseThrow();
        Instant from = incident.getOpenedAt().minus(Duration.ofMinutes(15));
        Instant to = incident.getLastBreachAt() == null ? incident.getOpenedAt() : incident.getLastBreachAt();
        String csv = metricsCsv(incident.getServiceId(), from, to.plusSeconds(30));
        List<AnalysisPrompts.PastIncident> history = jdbc.query("""
                SELECT i.title, i.opened_at, a.root_cause, i.resolution_note
                  FROM incidents i LEFT JOIN ai_analyses a ON a.incident_id = i.id
                 WHERE i.tenant_id = ? AND i.service_id = ? AND i.metric = ? AND i.status = 'RESOLVED' AND i.id <> ?
                 ORDER BY i.opened_at DESC
                 LIMIT 5
                """, (rs, n) -> new AnalysisPrompts.PastIncident(rs.getString("title"),
                        rs.getTimestamp("opened_at").toInstant().toString(), rs.getString("root_cause"),
                        rs.getString("resolution_note")),
                incident.getTenantId(), incident.getServiceId(), incident.getMetric().name(), incidentId);
        return AnalysisPrompts.diagnosis(service.getName(), service.getHostname(), incident.getTitle(),
                incident.condition().describe(), incident.getPeakValue(), incident.getOpenedAt().toString(), csv, history);
    }

    private String metricsCsv(Long serviceId, Instant from, Instant to) {
        List<String> rows = jdbc.query("""
                SELECT date_bin(INTERVAL '30 seconds', recorded_at, TIMESTAMPTZ '2000-01-01') AS t,
                       avg(cpu) AS cpu, avg(memory) AS memory, avg(disk) AS disk,
                       avg(latency_ms) AS latency, avg(error_rate) AS errors
                  FROM metric_samples
                 WHERE service_id = ? AND recorded_at BETWEEN ? AND ?
                 GROUP BY 1 ORDER BY 1
                """, (rs, n) -> String.join(",", rs.getTimestamp("t").toInstant().toString(),
                        fmt(rs.getObject("cpu")), fmt(rs.getObject("memory")), fmt(rs.getObject("disk")),
                        fmt(rs.getObject("latency")), fmt(rs.getObject("errors"))),
                serviceId, Timestamp.from(from), Timestamp.from(to));
        return rows.isEmpty() ? "(no samples)" : String.join("\n", rows);
    }

    ParsedDiagnosis parseDiagnosis(String text) {
        try {
            String json = text.strip();
            if (json.startsWith("```")) { // some models wrap JSON in a markdown fence despite instructions
                json = json.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
            }
            JsonNode node = mapper.readTree(json);
            String rootCause = node.path("root_cause").asText("").strip();
            if (rootCause.isEmpty()) {
                throw new IllegalArgumentException("missing root_cause");
            }
            int confidence = node.path("confidence").asInt(0);
            List<String> actions = new ArrayList<>();
            node.path("suggested_actions").forEach(a -> {
                if (!a.asText("").isBlank() && actions.size() < 6) actions.add(a.asText().strip());
            });
            return new ParsedDiagnosis(rootCause, confidence, actions);
        } catch (Exception e) {
            throw new GeminiClient.GeminiException("AI returned an unexpected format: " + e.getMessage(), 1, e);
        }
    }

    // ------------------------------------------------------------------ post-mortem

    public void markPostmortemPending(Long incidentId) {
        tx.executeWithoutResult(status -> analyses.findByIncidentId(incidentId).ifPresent(AiAnalysis::markPostmortemPending));
    }

    public void writePostmortem(Long incidentId) {
        String prompt = tx.execute(status -> buildPostmortemPrompt(incidentId));
        if (prompt == null) {
            return;
        }
        try {
            String text = gemini.generate(prompt, false).text().strip();
            tx.executeWithoutResult(status -> {
                analyses.findByIncidentId(incidentId).ifPresent(a -> a.completePostmortem(text));
                events.save(new IncidentEvent(incidentId, IncidentEventType.POSTMORTEM_READY,
                        "Post-mortem draft is ready", null, Instant.now()));
            });
        } catch (RuntimeException e) {
            log.warn("Post-mortem failed for incident {}: {}", incidentId, e.getMessage());
            tx.executeWithoutResult(status ->
                    analyses.findByIncidentId(incidentId).ifPresent(a -> a.failPostmortem("Post-mortem failed: " + e.getMessage())));
        }
    }

    private String buildPostmortemPrompt(Long incidentId) {
        Incident incident = incidents.findById(incidentId).orElse(null);
        if (incident == null || incident.getResolvedAt() == null) {
            return null;
        }
        String serviceName = services.findById(incident.getServiceId()).map(MonitoredService::getName).orElse("unknown");
        String rootCause = analyses.findByIncidentId(incidentId).map(AiAnalysis::getRootCause).orElse(null);
        String timeline = events.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId).stream()
                .map(e -> "- " + e.getCreatedAt() + " " + e.getType() + ": " + e.getMessage())
                .collect(Collectors.joining("\n"));
        String resolution = incident.getResolvedBy() == null
                ? "Auto-resolved: the metric returned to normal for the rule's full window."
                : "Resolved by an engineer" + (incident.getResolutionNote() == null ? "." : ": " + incident.getResolutionNote());
        Duration duration = Duration.between(incident.getOpenedAt(), incident.getResolvedAt());
        return AnalysisPrompts.postmortem(serviceName, incident.getTitle(), incident.getOpenedAt().toString(),
                incident.getResolvedAt().toString(), humanize(duration), rootCause, resolution, timeline);
    }

    /** "40 seconds", "1 minute 40 seconds", "2 hours 5 minutes": exact enough that the model does not round. */
    static String humanize(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        if (hours > 0) {
            return plural(hours, "hour") + (minutes > 0 ? " " + plural(minutes, "minute") : "");
        }
        if (minutes > 0) {
            return plural(minutes, "minute") + (seconds > 0 ? " " + plural(seconds, "second") : "");
        }
        return plural(seconds, "second");
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    private static String fmt(Object value) {
        return value == null ? "" : String.format("%.1f", ((Number) value).doubleValue());
    }
}
