package com.pulseops.services;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    List<MonitoredService> findByTenantIdOrderByNameAsc(Long tenantId);

    Optional<MonitoredService> findByIdAndTenantId(Long id, Long tenantId);

    List<MonitoredService> findByIdIn(Collection<Long> ids);
}
