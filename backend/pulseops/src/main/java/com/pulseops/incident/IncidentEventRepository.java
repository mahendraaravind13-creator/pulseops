package com.pulseops.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, Long> {

    List<IncidentEvent> findByIncidentIdOrderByCreatedAtAscIdAsc(Long incidentId);
}
