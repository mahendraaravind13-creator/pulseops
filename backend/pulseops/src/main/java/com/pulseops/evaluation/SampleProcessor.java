package com.pulseops.evaluation;

import com.pulseops.evaluation.RuleEvaluator.Verdict;
import com.pulseops.incident.IncidentService;
import com.pulseops.ingest.SampleMessage;
import com.pulseops.rules.AlertRule;
import com.pulseops.rules.AlertRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Processes one sample in one database transaction: store it, then evaluate every rule that applies to its service.
 * Either all of it commits (sample row, incident changes, timeline events) or none of it does, and the Kafka offset
 * is only committed afterwards. A crash in the middle therefore means "reprocess the sample", never "half-processed".
 */
@Service
public class SampleProcessor {

    private final SampleStore store;
    private final AlertRuleRepository rules;
    private final IncidentService incidents;

    public SampleProcessor(SampleStore store, AlertRuleRepository rules, IncidentService incidents) {
        this.store = store;
        this.rules = rules;
        this.incidents = incidents;
    }

    public record Result(long serviceId, boolean duplicate) {
    }

    @Transactional
    public Result process(SampleMessage sample) {
        long serviceId = store.upsertService(sample.tenantId(), sample.service(), sample.hostname(), sample.recordedAt());
        if (!store.insertSample(serviceId, sample)) {
            return new Result(serviceId, true);
        }

        List<AlertRule> applicable = rules.findActiveForService(sample.tenantId(), serviceId);
        if (applicable.isEmpty()) {
            return new Result(serviceId, false);
        }

        Instant at = sample.recordedAt();
        int longestWindow = applicable.stream().mapToInt(AlertRule::getDurationSeconds).max().orElse(0);
        List<SamplePoint> window = store.window(serviceId, at.minusSeconds(longestWindow), at);

        for (AlertRule rule : applicable) {
            Verdict verdict = RuleEvaluator.evaluate(rule.condition(), window, at);
            switch (verdict) {
                case BREACHING -> {
                    double value = RuleEvaluator.latestValue(rule.condition(), window, at);
                    incidents.openOrRecordBreach(rule, serviceId, sample.service(), value, at);
                }
                case RECOVERED -> incidents.autoResolveIfActive(rule.getId(), serviceId, at);
                case NO_CHANGE -> { }
            }
        }
        return new Result(serviceId, false);
    }
}
