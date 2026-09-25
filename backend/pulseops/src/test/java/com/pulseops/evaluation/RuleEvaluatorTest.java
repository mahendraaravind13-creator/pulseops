package com.pulseops.evaluation;

import com.pulseops.evaluation.RuleEvaluator.Verdict;
import com.pulseops.rules.Metric;
import com.pulseops.rules.Operator;
import com.pulseops.rules.RuleCondition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {

    private static final Instant T = Instant.parse("2026-09-25T10:00:00Z");
    private static final RuleCondition CPU_ABOVE_85_FOR_60S = new RuleCondition(Metric.CPU, Operator.GT, 85, 60);

    /** Samples every {@code step} seconds, ending at T, covering {@code spanSeconds}. */
    private static List<SamplePoint> cpuSeries(int spanSeconds, int step, double... values) {
        List<SamplePoint> points = new ArrayList<>();
        int n = spanSeconds / step + 1;
        for (int i = 0; i < n; i++) {
            double v = values[Math.min(i, values.length - 1)];
            points.add(new SamplePoint(T.minusSeconds(spanSeconds - (long) i * step), v, 40.0, null, null, null));
        }
        return points;
    }

    @Test
    void breachesWhenEverySampleInAFullWindowIsAboveThreshold() {
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, cpuSeries(60, 5, 90), T)).isEqualTo(Verdict.BREACHING);
    }

    @Test
    void oneHealthySampleInTheWindowPreventsBreach() {
        List<SamplePoint> samples = cpuSeries(60, 5, 90);
        samples.set(6, new SamplePoint(samples.get(6).recordedAt(), 50.0, 40.0, null, null, null));
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, samples, T)).isEqualTo(Verdict.NO_CHANGE);
    }

    @Test
    void shortBurstDoesNotCountAsSustainedBreach() {
        // 20 seconds of high CPU right after the agent started: not "60 seconds of high CPU".
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, cpuSeries(20, 5, 99), T)).isEqualTo(Verdict.NO_CHANGE);
    }

    @Test
    void windowWithinToleranceStillCounts() {
        // Data spans 50s of a 60s window (agent reports every 10s, first sample slightly late): tolerance is 15s.
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, cpuSeries(50, 10, 90), T)).isEqualTo(Verdict.BREACHING);
    }

    @Test
    void recoversOnlyAfterAFullHealthyWindow() {
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, cpuSeries(60, 5, 30), T)).isEqualTo(Verdict.RECOVERED);
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, cpuSeries(20, 5, 30), T)).isEqualTo(Verdict.NO_CHANGE);
    }

    @Test
    void samplesOutsideTheWindowAreIgnored() {
        List<SamplePoint> samples = new ArrayList<>(cpuSeries(60, 5, 90));
        samples.add(new SamplePoint(T.minusSeconds(120), 10.0, 40.0, null, null, null)); // old, healthy
        samples.add(new SamplePoint(T.plusSeconds(10), 10.0, 40.0, null, null, null));   // newer than T
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, samples, T)).isEqualTo(Verdict.BREACHING);
    }

    @Test
    void thresholdIsExclusive() {
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, cpuSeries(60, 5, 85), T)).isEqualTo(Verdict.RECOVERED);
    }

    @Test
    void lessThanOperator() {
        RuleCondition memoryBelow5 = new RuleCondition(Metric.MEMORY, Operator.LT, 50, 30);
        List<SamplePoint> low = cpuSeries(30, 5, 10); // memory is 40 in every sample
        assertThat(RuleEvaluator.evaluate(memoryBelow5, low, T)).isEqualTo(Verdict.BREACHING);
    }

    @Test
    void zeroDurationUsesOnlyTheNewestSample() {
        RuleCondition instant = new RuleCondition(Metric.CPU, Operator.GT, 85, 0);
        List<SamplePoint> samples = List.of(new SamplePoint(T, 95.0, 40.0, null, null, null));
        assertThat(RuleEvaluator.evaluate(instant, samples, T)).isEqualTo(Verdict.BREACHING);
        List<SamplePoint> healthy = List.of(new SamplePoint(T, 20.0, 40.0, null, null, null));
        assertThat(RuleEvaluator.evaluate(instant, healthy, T)).isEqualTo(Verdict.RECOVERED);
    }

    @Test
    void optionalMetricMissingFromSamplesMeansNoChange() {
        RuleCondition disk = new RuleCondition(Metric.DISK, Operator.GT, 90, 60);
        assertThat(RuleEvaluator.evaluate(disk, cpuSeries(60, 5, 99), T)).isEqualTo(Verdict.NO_CHANGE);
    }

    @Test
    void emptyWindowMeansNoChange() {
        assertThat(RuleEvaluator.evaluate(CPU_ABOVE_85_FOR_60S, List.of(), T)).isEqualTo(Verdict.NO_CHANGE);
    }

    @Test
    void latestValueReturnsNewestReading() {
        List<SamplePoint> samples = cpuSeries(20, 10, 88, 91, 97);
        assertThat(RuleEvaluator.latestValue(CPU_ABOVE_85_FOR_60S, samples, T)).isEqualTo(97.0);
    }
}
