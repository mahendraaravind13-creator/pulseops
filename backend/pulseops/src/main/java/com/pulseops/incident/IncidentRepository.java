package com.pulseops.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIdAndTenantId(Long id, Long tenantId);

    @Query("select i from Incident i where i.serviceId = :serviceId and i.ruleId = :ruleId and i.status <> com.pulseops.incident.IncidentStatus.RESOLVED")
    Optional<Incident> findActive(@Param("serviceId") Long serviceId, @Param("ruleId") Long ruleId);

    @Query("select i from Incident i where i.ruleId = :ruleId and i.status <> com.pulseops.incident.IncidentStatus.RESOLVED")
    List<Incident> findActiveForRule(@Param("ruleId") Long ruleId);

    @Query("select count(i) from Incident i where i.tenantId = :tenantId and i.ruleId = :ruleId and i.status <> com.pulseops.incident.IncidentStatus.RESOLVED")
    long countActiveForRule(@Param("tenantId") Long tenantId, @Param("ruleId") Long ruleId);

    interface RuleCount {
        Long getRuleId();

        Long getCount();
    }

    @Query("""
            select i.ruleId as ruleId, count(i) as count from Incident i
            where i.tenantId = :tenantId and i.status <> com.pulseops.incident.IncidentStatus.RESOLVED and i.ruleId is not null
            group by i.ruleId
            """)
    List<RuleCount> countActiveByRule(@Param("tenantId") Long tenantId);
}
