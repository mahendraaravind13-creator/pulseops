package com.pulseops.rules;

import com.pulseops.evaluation.SamplePoint;

public enum Metric {
    CPU("CPU", "%"),
    MEMORY("Memory", "%"),
    DISK("Disk", "%"),
    LATENCY_MS("Latency", "ms"),
    ERROR_RATE("Error rate", "%");

    private final String label;
    private final String unit;

    Metric(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    /** Reads this metric from a sample. Optional metrics (disk, latency, error rate) may be null. */
    public Double valueOf(SamplePoint sample) {
        return switch (this) {
            case CPU -> sample.cpu();
            case MEMORY -> sample.memory();
            case DISK -> sample.disk();
            case LATENCY_MS -> sample.latencyMs();
            case ERROR_RATE -> sample.errorRate();
        };
    }
}
