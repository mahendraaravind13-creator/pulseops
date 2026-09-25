package com.pulseops.evaluation;

import java.time.Instant;

public record SamplePoint(Instant recordedAt, Double cpu, Double memory, Double disk, Double latencyMs, Double errorRate) {
}
