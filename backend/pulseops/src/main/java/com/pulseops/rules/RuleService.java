package com.pulseops.rules;

import com.pulseops.common.BadRequestException;
import com.pulseops.common.NotFoundException;
import com.pulseops.incident.IncidentRepository;
import com.pulseops.incident.IncidentService;
import com.pulseops.rules.RuleDtos.RuleRequest;
import com.pulseops.rules.RuleDtos.RuleResponse;
import com.pulseops.services.MonitoredService;
import com.pulseops.services.MonitoredServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleService {

    private final AlertRuleRepository rules;
    private final MonitoredServiceRepository services;
    private final IncidentRepository incidents;
    private final IncidentService incidentService;

    public RuleService(AlertRuleRepository rules, MonitoredServiceRepository services,
                       IncidentRepository incidents, IncidentService incidentService) {
        this.rules = rules;
        this.services = services;
        this.incidents = incidents;
        this.incidentService = incidentService;
    }

    /** Sensible starting rules so a new tenant sees incidents as soon as the agent reports a problem. */
    @Transactional
    public void createDefaultRules(Long tenantId) {
        save(tenantId, "High CPU", Metric.CPU, 85, 60, Severity.CRITICAL);
        save(tenantId, "High memory", Metric.MEMORY, 90, 120, Severity.CRITICAL);
        save(tenantId, "Disk almost full", Metric.DISK, 90, 300, Severity.WARNING);
    }

    private void save(Long tenantId, String name, Metric metric, double threshold, int duration, Severity severity) {
        AlertRule rule = new AlertRule(tenantId);
        rule.update(name, null, metric, Operator.GT, threshold, duration, severity, true);
        rules.save(rule);
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list(Long tenantId) {
        List<AlertRule> all = rules.findByTenantIdOrderByCreatedAtAsc(tenantId);
        Map<Long, String> serviceNames = services.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(MonitoredService::getId, MonitoredService::getName));
        Map<Long, Long> activeCounts = incidents.countActiveByRule(tenantId).stream()
                .collect(Collectors.toMap(IncidentRepository.RuleCount::getRuleId, IncidentRepository.RuleCount::getCount));
        return all.stream().map(r -> toResponse(r, serviceNames, activeCounts)).toList();
    }

    @Transactional
    public RuleResponse create(Long tenantId, RuleRequest request) {
        AlertRule rule = new AlertRule(tenantId);
        apply(tenantId, rule, request);
        return single(tenantId, rules.save(rule));
    }

    @Transactional
    public RuleResponse update(Long tenantId, Long id, RuleRequest request) {
        AlertRule rule = get(tenantId, id);
        apply(tenantId, rule, request);
        return single(tenantId, rule);
    }

    @Transactional
    public RuleResponse setEnabled(Long tenantId, Long id, boolean enabled) {
        AlertRule rule = get(tenantId, id);
        rule.setEnabled(enabled);
        return single(tenantId, rule);
    }

    /**
     * Resolves the rule's open incidents before deleting it, in the same transaction. Incidents keep a snapshot
     * of the rule (name, metric, threshold), so history stays readable after the rule is gone.
     */
    @Transactional
    public void delete(Long tenantId, Long id) {
        AlertRule rule = get(tenantId, id);
        incidentService.resolveAllForDeletedRule(rule);
        rules.delete(rule);
    }

    private AlertRule get(Long tenantId, Long id) {
        return rules.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Rule", id));
    }

    private void apply(Long tenantId, AlertRule rule, RuleRequest r) {
        if (r.serviceId() != null && services.findByIdAndTenantId(r.serviceId(), tenantId).isEmpty()) {
            throw new BadRequestException("Service " + r.serviceId() + " does not exist");
        }
        if (isPercentage(r.metric()) && r.threshold() > 100) {
            throw new BadRequestException(r.metric().label() + " threshold must be between 0 and 100");
        }
        rule.update(r.name().trim(), r.serviceId(), r.metric(), r.operator(), r.threshold(), r.durationSeconds(),
                r.severity(), r.enabled() == null || r.enabled());
    }

    private static boolean isPercentage(Metric metric) {
        return metric != Metric.LATENCY_MS;
    }

    private RuleResponse single(Long tenantId, AlertRule rule) {
        Map<Long, String> names = rule.getServiceId() == null ? Map.of()
                : services.findById(rule.getServiceId()).stream()
                        .collect(Collectors.toMap(MonitoredService::getId, MonitoredService::getName));
        long active = rule.getId() == null ? 0 : incidents.countActiveForRule(tenantId, rule.getId());
        return toResponse(rule, names, rule.getId() == null ? Map.of() : Map.of(rule.getId(), active));
    }

    private static RuleResponse toResponse(AlertRule r, Map<Long, String> serviceNames, Map<Long, Long> activeCounts) {
        return new RuleResponse(r.getId(), r.getName(), r.getServiceId(),
                r.getServiceId() == null ? null : serviceNames.get(r.getServiceId()),
                r.getMetric(), r.getOperator(), r.getThreshold(), r.getDurationSeconds(), r.getSeverity(),
                r.isEnabled(), activeCounts.getOrDefault(r.getId(), 0L), r.getCreatedAt(), r.getUpdatedAt());
    }
}
