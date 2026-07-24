package com.pulseops.analyzer.repository;

import com.pulseops.analyzer.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    // Find all incidents for a specific tenant, newest first
    List<Incident> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    // Find all incidents for a specific service
    List<Incident> findByServiceNameOrderByCreatedAtDesc(String serviceName);
}