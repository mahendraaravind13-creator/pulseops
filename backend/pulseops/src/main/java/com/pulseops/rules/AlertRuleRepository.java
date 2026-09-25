package com.pulseops.rules;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByTenantIdOrderByCreatedAtAsc(Long tenantId);

    Optional<AlertRule> findByIdAndTenantId(Long id, Long tenantId);

    /** Rules the evaluator must check for one incoming sample: tenant-wide rules plus rules pinned to this service. */
    @Query("""
            select r from AlertRule r
            where r.tenantId = :tenantId and r.enabled = true
              and (r.serviceId is null or r.serviceId = :serviceId)
            """)
    List<AlertRule> findActiveForService(@Param("tenantId") Long tenantId, @Param("serviceId") Long serviceId);
}
