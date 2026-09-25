package com.pulseops.incident;

import com.pulseops.auth.AuthenticatedUser;
import com.pulseops.config.PulseOpsProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Aggregates for the overview page, computed in SQL (FILTER clauses, generate_series) rather than in Java loops. */
@RestController
@RequestMapping("/api/v1/overview")
public class OverviewController {

    private final JdbcTemplate jdbc;
    private final PulseOpsProperties properties;
    private final Clock clock;

    public OverviewController(JdbcTemplate jdbc, PulseOpsProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public record DayCount(LocalDate date, long count) {
    }

    public record Overview(long openIncidents, long acknowledgedIncidents, long criticalOpen, long servicesTotal,
                           long servicesReporting, Long mttaSeconds, Long mttrSeconds, List<DayCount> incidentsPerDay) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Overview overview(@AuthenticationPrincipal AuthenticatedUser user) {
        Long tenantId = user.tenantId();
        Instant now = clock.instant();
        Timestamp weekAgo = Timestamp.from(now.minusSeconds(7 * 24 * 3600));

        long[] active = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE status = 'OPEN'),
                       count(*) FILTER (WHERE status = 'ACKNOWLEDGED'),
                       count(*) FILTER (WHERE severity = 'CRITICAL')
                  FROM incidents
                 WHERE tenant_id = ? AND status <> 'RESOLVED'
                """, (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, tenantId);

        long[] svc = jdbc.queryForObject("""
                SELECT count(*), count(*) FILTER (WHERE last_seen_at >= ?)
                  FROM services WHERE tenant_id = ?
                """, (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)},
                Timestamp.from(now.minus(properties.monitoring().staleAfter())), tenantId);

        // MTTA = mean time to acknowledge, MTTR = mean time to resolve, over incidents opened in the last 7 days.
        Long[] means = jdbc.queryForObject("""
                SELECT round(avg(extract(epoch FROM acknowledged_at - opened_at)))::bigint,
                       round(avg(extract(epoch FROM resolved_at - opened_at)))::bigint
                  FROM incidents
                 WHERE tenant_id = ? AND opened_at >= ?
                """, (rs, n) -> new Long[]{(Long) rs.getObject(1), (Long) rs.getObject(2)}, tenantId, weekAgo);

        List<DayCount> perDay = jdbc.query("""
                SELECT d::date AS day, count(i.id) AS n
                  FROM generate_series((now() AT TIME ZONE 'UTC')::date - 6, (now() AT TIME ZONE 'UTC')::date, INTERVAL '1 day') AS d
                  LEFT JOIN incidents i
                         ON i.tenant_id = ? AND (i.opened_at AT TIME ZONE 'UTC')::date = d::date
                 GROUP BY d
                 ORDER BY d
                """, (rs, n) -> new DayCount(rs.getDate("day").toLocalDate(), rs.getLong("n")), tenantId);

        return new Overview(active[0], active[1], active[2], svc[0], svc[1], means[0], means[1], perDay);
    }
}
