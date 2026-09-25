package com.pulseops.rules;

/** "metric operator threshold for durationSeconds", e.g. CPU GT 85 for 60s. */
public record RuleCondition(Metric metric, Operator operator, double threshold, int durationSeconds) {

    public String describe() {
        String value = threshold == Math.rint(threshold) ? String.valueOf((long) threshold) : String.valueOf(threshold);
        String duration = durationSeconds == 0 ? "" : " for " + durationSeconds + "s";
        return metric.label() + " " + operator.word() + " " + value + metric.unit() + duration;
    }
}
