package com.pulseops.ingestor.repository;

import com.pulseops.ingestor.model.TelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryEvent, Long> {

    // Grabs the single newest telemetry log for each service belonging to this tenant
    @Query(value = "SELECT DISTINCT ON (service_name) * FROM telemetry_logs WHERE tenant_id = :tenantId ORDER BY service_name, timestamp DESC", nativeQuery = true)
    List<TelemetryEvent> findLatestByTenantId(@Param("tenantId") String tenantId);
}