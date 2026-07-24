package com.pulseops.analyzer.repository;

import com.pulseops.analyzer.model.IncidentStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentStepRepository extends JpaRepository<IncidentStep, Long> {
    List<IncidentStep> findByIncidentIdOrderByStepNumberAsc(Long incidentId);
}