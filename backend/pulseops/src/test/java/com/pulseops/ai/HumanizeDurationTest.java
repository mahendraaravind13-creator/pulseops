package com.pulseops.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HumanizeDurationTest {

    @Test
    void formatsDurationsWithoutRounding() {
        assertThat(AnalysisService.humanize(Duration.ofSeconds(40))).isEqualTo("40 seconds");
        assertThat(AnalysisService.humanize(Duration.ofSeconds(100))).isEqualTo("1 minute 40 seconds");
        assertThat(AnalysisService.humanize(Duration.ofSeconds(120))).isEqualTo("2 minutes");
        assertThat(AnalysisService.humanize(Duration.ofMinutes(125))).isEqualTo("2 hours 5 minutes");
        assertThat(AnalysisService.humanize(Duration.ofSeconds(1))).isEqualTo("1 second");
    }
}
