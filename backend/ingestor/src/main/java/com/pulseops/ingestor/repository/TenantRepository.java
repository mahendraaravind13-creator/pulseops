package com.pulseops.ingestor.repository;

import com.pulseops.ingestor.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByEmail(String email);
    Optional<Tenant> findByApiKey(String apiKey);
    boolean existsByEmail(String email);
    boolean existsByTenantId(String tenantId);
}