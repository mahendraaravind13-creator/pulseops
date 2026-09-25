package com.pulseops.incident;

import com.pulseops.auth.AuthenticatedUser;
import com.pulseops.common.BadRequestException;
import com.pulseops.common.PageResponse;
import com.pulseops.incident.IncidentDtos.IncidentDetail;
import com.pulseops.incident.IncidentDtos.IncidentSummary;
import com.pulseops.incident.IncidentDtos.NoteRequest;
import com.pulseops.incident.IncidentDtos.ResolveRequest;
import com.pulseops.incident.IncidentDtos.VersionRequest;
import com.pulseops.rules.Severity;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentQueries queries;

    public IncidentController(IncidentService incidentService, IncidentQueries queries) {
        this.incidentService = incidentService;
        this.queries = queries;
    }

    @GetMapping
    public PageResponse<IncidentSummary> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        var filter = new IncidentQueries.Filter(parseStatuses(status), severity, serviceId, q, from, to);
        return queries.list(user.tenantId(), filter, page, size, sort);
    }

    @GetMapping("/{id}")
    public IncidentDetail get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return queries.detail(user.tenantId(), id);
    }

    @PostMapping("/{id}/acknowledge")
    public IncidentDetail acknowledge(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                      @Valid @RequestBody VersionRequest request) {
        incidentService.acknowledge(user, id, request.version());
        return queries.detail(user.tenantId(), id);
    }

    @PostMapping("/{id}/resolve")
    public IncidentDetail resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                  @Valid @RequestBody ResolveRequest request) {
        incidentService.resolve(user, id, request.version(), request.note());
        return queries.detail(user.tenantId(), id);
    }

    @PostMapping("/{id}/notes")
    public IncidentDetail addNote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                  @Valid @RequestBody NoteRequest request) {
        incidentService.addNote(user, id, request.message());
        return queries.detail(user.tenantId(), id);
    }

    @PostMapping("/{id}/analysis/retry")
    public ResponseEntity<IncidentDetail> retryAnalysis(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable Long id) {
        incidentService.retryAnalysis(user.tenantId(), id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(queries.detail(user.tenantId(), id));
    }

    @PostMapping("/{id}/postmortem/retry")
    public ResponseEntity<IncidentDetail> retryPostmortem(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @PathVariable Long id) {
        incidentService.retryPostmortem(user.tenantId(), id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(queries.detail(user.tenantId(), id));
    }

    private static List<IncidentStatus> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .map(IncidentStatus::valueOf).toList();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be a comma-separated list of OPEN, ACKNOWLEDGED, RESOLVED");
        }
    }
}
