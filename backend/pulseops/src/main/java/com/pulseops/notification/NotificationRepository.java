package com.pulseops.notification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByTenantIdOrderByCreatedAtDescIdDesc(Long tenantId, Limit limit);

    long countByTenantIdAndReadFalse(Long tenantId);

    Optional<Notification> findByIdAndTenantId(Long id, Long tenantId);

    @Modifying
    @Query("update Notification n set n.read = true where n.tenantId = :tenantId and n.read = false")
    int markAllRead(@Param("tenantId") Long tenantId);
}
