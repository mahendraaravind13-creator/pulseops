package com.pulseops.incident;

import com.pulseops.common.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentStateTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void openCanBeAcknowledgedThenResolved() {
        Incident incident = Incident.forTest(IncidentStatus.OPEN);
        incident.acknowledge(7L, NOW);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(incident.getAcknowledgedBy()).isEqualTo(7L);

        incident.resolve(7L, "rolled back", NOW.plusSeconds(60));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolutionNote()).isEqualTo("rolled back");
    }

    @Test
    void openCanBeResolvedDirectly() {
        Incident incident = Incident.forTest(IncidentStatus.OPEN);
        incident.resolve(7L, null, NOW);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void autoResolveLeavesResolverEmpty() {
        Incident incident = Incident.forTest(IncidentStatus.ACKNOWLEDGED);
        incident.autoResolve(NOW);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedBy()).isNull();
    }

    @Test
    void cannotAcknowledgeTwice() {
        Incident incident = Incident.forTest(IncidentStatus.ACKNOWLEDGED);
        assertThatThrownBy(() -> incident.acknowledge(1L, NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotChangeAResolvedIncident() {
        Incident incident = Incident.forTest(IncidentStatus.RESOLVED);
        assertThatThrownBy(() -> incident.acknowledge(1L, NOW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> incident.resolve(1L, null, NOW)).isInstanceOf(ConflictException.class);
    }
}
