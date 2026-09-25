package com.pulseops.evaluation;

import com.pulseops.rules.RuleCondition;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Decides whether a rule is breaching, recovered, or neither, given the samples in its window.
 * Pure function: no database, no clock, no Spring. That makes the core business rule exhaustively unit-testable.
 *
 * <p>Semantics of "CPU > 85 for 60s", evaluated at time T (the newest sample's timestamp, not the wall clock, so a
 * delayed queue does not change the outcome):
 * <ul>
 *   <li>BREACHING: every sample in [T-60s, T] exceeds 85, and those samples actually span the window
 *       (a single high sample right after the agent started is not "60 seconds of high CPU").</li>
 *   <li>RECOVERED: no sample in the window exceeds 85, with the same coverage requirement. This is the auto-resolve
 *       signal, and requiring a full healthy window prevents flapping open/resolve/open.</li>
 *   <li>NO_CHANGE: mixed values or not enough data. Nothing happens.</li>
 * </ul>
 * A duration of 0 means "evaluate the single newest sample".
 */
public final class RuleEvaluator {

    public enum Verdict { BREACHING, RECOVERED, NO_CHANGE }

    private static final Duration MIN_TOLERANCE = Duration.ofSeconds(10);

    private RuleEvaluator() {
    }

    /**
     * @param samples samples of one service, any order, may include samples outside the window
     * @param at      evaluation time (timestamp of the sample that triggered evaluation)
     */
    public static Verdict evaluate(RuleCondition condition, List<SamplePoint> samples, Instant at) {
        Instant windowStart = at.minusSeconds(condition.durationSeconds());
        List<SamplePoint> window = samples.stream()
                .filter(s -> !s.recordedAt().isBefore(windowStart) && !s.recordedAt().isAfter(at))
                .filter(s -> condition.metric().valueOf(s) != null)
                .toList();
        if (window.isEmpty()) {
            return Verdict.NO_CHANGE;
        }

        if (condition.durationSeconds() == 0) {
            SamplePoint newest = window.stream().max((a, b) -> a.recordedAt().compareTo(b.recordedAt())).orElseThrow();
            return breaches(condition, newest) ? Verdict.BREACHING : Verdict.RECOVERED;
        }

        if (!coversWindow(window, at, condition.durationSeconds())) {
            return Verdict.NO_CHANGE;
        }
        long breaching = window.stream().filter(s -> breaches(condition, s)).count();
        if (breaching == window.size()) {
            return Verdict.BREACHING;
        }
        if (breaching == 0) {
            return Verdict.RECOVERED;
        }
        return Verdict.NO_CHANGE;
    }

    /** The newest breaching value in the window, used as the incident's "value at open". */
    public static double latestValue(RuleCondition condition, List<SamplePoint> samples, Instant at) {
        return samples.stream()
                .filter(s -> !s.recordedAt().isAfter(at))
                .filter(s -> condition.metric().valueOf(s) != null)
                .max((a, b) -> a.recordedAt().compareTo(b.recordedAt()))
                .map(s -> condition.metric().valueOf(s))
                .orElse(Double.NaN);
    }

    /**
     * The oldest sample must be close to the window start. Agents report every few seconds, so allow a tolerance of
     * a quarter of the window (at least 10s): with a 60s window, data spanning 45s or more counts as full coverage.
     */
    static boolean coversWindow(List<SamplePoint> window, Instant at, int durationSeconds) {
        Instant oldest = window.stream().map(SamplePoint::recordedAt).filter(Objects::nonNull).min(Instant::compareTo).orElse(at);
        Duration span = Duration.between(oldest, at);
        Duration duration = Duration.ofSeconds(durationSeconds);
        Duration tolerance = duration.dividedBy(4);
        if (tolerance.compareTo(MIN_TOLERANCE) < 0) {
            tolerance = MIN_TOLERANCE;
        }
        return span.compareTo(duration.minus(tolerance)) >= 0;
    }

    private static boolean breaches(RuleCondition condition, SamplePoint sample) {
        return condition.operator().breaches(condition.metric().valueOf(sample), condition.threshold());
    }
}
