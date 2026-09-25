package com.pulseops.incident;

import com.pulseops.ai.AiAnalysis;
import com.pulseops.ai.AiAnalysisRepository;
import com.pulseops.auth.User;
import com.pulseops.auth.UserRepository;
import com.pulseops.common.BadRequestException;
import com.pulseops.common.NotFoundException;
import com.pulseops.common.PageResponse;
import com.pulseops.incident.IncidentDtos.AnalysisView;
import com.pulseops.incident.IncidentDtos.Condition;
import com.pulseops.incident.IncidentDtos.EventView;
import com.pulseops.incident.IncidentDtos.IncidentDetail;
import com.pulseops.incident.IncidentDtos.IncidentSummary;
import com.pulseops.rules.Metric;
import com.pulseops.rules.Severity;
import com.pulseops.services.MonitoredServiceRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read side for incidents. The list is plain SQL so the filters, sort and pagination are visible and map directly
 * onto the (tenant_id, status, opened_at) and (tenant_id, opened_at) indexes.
 */
@Service
public class IncidentQueries {

    /** Whitelist: user input never reaches ORDER BY directly (that would be SQL injection). */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "openedAt", "i.opened_at",
            "severity", "i.severity",
            "status", "i.status",
            "lastBreachAt", "i.last_breach_at");

    private final NamedParameterJdbcTemplate jdbc;
    private final IncidentRepository incidents;
    private final IncidentEventRepository events;
    private final AiAnalysisRepository analyses;
    private final UserRepository users;
    private final MonitoredServiceRepository services;

    public IncidentQueries(NamedParameterJdbcTemplate jdbc, IncidentRepository incidents, IncidentEventRepository events,
                           AiAnalysisRepository analyses, UserRepository users, MonitoredServiceRepository services) {
        this.jdbc = jdbc;
        this.incidents = incidents;
        this.events = events;
        this.analyses = analyses;
        this.users = users;
        this.services = services;
    }

    public record Filter(List<IncidentStatus> statuses, Severity severity, Long serviceId, String query,
                         Instant from, Instant to) {
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentSummary> list(Long tenantId, Filter filter, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("page must be >= 0 and size between 1 and 100");
        }
        StringBuilder where = new StringBuilder(" WHERE i.tenant_id = :tenantId");
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            where.append(" AND i.status IN (:statuses)");
            params.addValue("statuses", filter.statuses().stream().map(Enum::name).toList());
        }
        if (filter.severity() != null) {
            where.append(" AND i.severity = :severity");
            params.addValue("severity", filter.severity().name());
        }
        if (filter.serviceId() != null) {
            where.append(" AND i.service_id = :serviceId");
            params.addValue("serviceId", filter.serviceId());
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            where.append(" AND (i.title ILIKE :q OR s.name ILIKE :q)");
            params.addValue("q", "%" + escapeLike(filter.query().trim()) + "%");
        }
        if (filter.from() != null) {
            where.append(" AND i.opened_at >= :from");
            params.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND i.opened_at < :to");
            params.addValue("to", Timestamp.from(filter.to()));
        }

        String from = " FROM incidents i JOIN services s ON s.id = i.service_id";
        Long total = jdbc.queryForObject("SELECT count(*)" + from + where, params, Long.class);

        params.addValue("limit", size).addValue("offset", (long) page * size);
        String sql = """
                SELECT i.id, i.title, i.status, i.severity, i.service_id, s.name AS service_name, i.rule_id,
                       i.rule_name, i.metric, i.opened_at, i.acknowledged_at, i.resolved_at, i.last_breach_at,
                       i.peak_value, i.breach_count, i.version
                """ + from + where + " ORDER BY " + orderBy(sort) + ", i.id DESC LIMIT :limit OFFSET :offset";
        List<IncidentSummary> content = jdbc.query(sql, params, SUMMARY_MAPPER);
        return PageResponse.of(content, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public IncidentDetail detail(Long tenantId, Long id) {
        Incident i = incidents.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Incident", id));
        List<IncidentEvent> timeline = events.findByIncidentIdOrderByCreatedAtAscIdAsc(id);

        Set<Long> userIds = new HashSet<>();
        timeline.stream().map(IncidentEvent::getActorUserId).filter(Objects::nonNull).forEach(userIds::add);
        if (i.getAcknowledgedBy() != null) userIds.add(i.getAcknowledgedBy());
        if (i.getResolvedBy() != null) userIds.add(i.getResolvedBy());
        Map<Long, String> names = users.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));

        String serviceName = services.findById(i.getServiceId()).map(s -> s.getName()).orElse("unknown");
        AnalysisView analysis = analyses.findByIncidentId(id).map(IncidentQueries::toView).orElse(null);

        List<EventView> eventViews = timeline.stream()
                .map(e -> new EventView(e.getId(), e.getType(), e.getMessage(),
                        e.getActorUserId() == null ? null : names.get(e.getActorUserId()), e.getCreatedAt()))
                .toList();

        return new IncidentDetail(i.getId(), i.getTitle(), i.getStatus(), i.getSeverity(), i.getServiceId(), serviceName,
                i.getRuleId(), i.getRuleName(), i.getMetric(), i.getOpenedAt(), i.getAcknowledgedAt(), i.getResolvedAt(),
                i.getLastBreachAt(), i.getPeakValue(), i.getBreachCount(), i.getVersion(),
                new Condition(i.getMetric(), i.getOperator(), i.getThreshold(), i.getDurationSeconds()),
                i.getResolutionNote(), names.get(i.getAcknowledgedBy()), names.get(i.getResolvedBy()),
                i.getStatus() == IncidentStatus.RESOLVED && i.getResolvedBy() == null,
                eventViews, analysis);
    }

    private static AnalysisView toView(AiAnalysis a) {
        return new AnalysisView(a.getStatus(), a.getRootCause(), a.getConfidence(),
                a.getSuggestedActions() == null ? List.of() : a.getSuggestedActions(), a.getModel(), a.getError(),
                a.getPostmortem(), a.getPostmortemStatus(), a.getUpdatedAt());
    }

    private static String orderBy(String sort) {
        if (sort == null || sort.isBlank()) {
            return "i.opened_at DESC";
        }
        String[] parts = sort.split(",");
        String column = SORT_COLUMNS.get(parts[0].trim());
        if (column == null) {
            throw new BadRequestException("Unsupported sort field: " + parts[0]);
        }
        boolean asc = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc");
        return column + (asc ? " ASC" : " DESC");
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static final RowMapper<IncidentSummary> SUMMARY_MAPPER = (rs, n) -> new IncidentSummary(
            rs.getLong("id"), rs.getString("title"), IncidentStatus.valueOf(rs.getString("status")),
            Severity.valueOf(rs.getString("severity")), rs.getLong("service_id"), rs.getString("service_name"),
            (Long) rs.getObject("rule_id"), rs.getString("rule_name"), Metric.valueOf(rs.getString("metric")),
            instant(rs.getTimestamp("opened_at")), instant(rs.getTimestamp("acknowledged_at")),
            instant(rs.getTimestamp("resolved_at")), instant(rs.getTimestamp("last_breach_at")),
            rs.getDouble("peak_value"), rs.getInt("breach_count"), rs.getLong("version"));

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
