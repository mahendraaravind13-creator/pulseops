package com.pulseops.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.common.NotFoundException;
import com.pulseops.incident.Incident;
import com.pulseops.incident.IncidentRepository;
import com.pulseops.services.MonitoredServiceRepository;
import com.pulseops.tenant.Tenant;
import com.pulseops.tenant.TenantRepository;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final IncidentRepository incidents;
    private final MonitoredServiceRepository services;
    private final TenantRepository tenants;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public NotificationService(NotificationRepository notifications, IncidentRepository incidents,
                               MonitoredServiceRepository services, TenantRepository tenants, JdbcTemplate jdbc,
                               ObjectMapper mapper) {
        this.notifications = notifications;
        this.incidents = incidents;
        this.services = services;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record NotificationView(Long id, String type, String title, String body, Long incidentId, boolean read,
                                   Instant createdAt) {
    }

    public record NotificationList(long unreadCount, List<NotificationView> items) {
    }

    /**
     * Creates the in-app notification and, if the tenant configured a webhook, a PENDING delivery row, in one new
     * transaction. The delivery row is the durable "to do" item: WebhookDispatcher sends it later with retries, so
     * a webhook endpoint that is down cannot lose the event or slow down incident processing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyIncident(Long incidentId, String type) {
        Incident incident = incidents.findById(incidentId).orElse(null);
        if (incident == null) {
            return;
        }
        String serviceName = services.findById(incident.getServiceId()).map(s -> s.getName()).orElse("unknown");
        String title = switch (type) {
            case "INCIDENT_OPENED" -> "[" + incident.getSeverity() + "] Incident opened on " + serviceName;
            case "INCIDENT_RESOLVED" -> "Incident resolved on " + serviceName;
            default -> "Incident update on " + serviceName;
        };
        String body = incident.getTitle();
        Notification saved = notifications.save(new Notification(incident.getTenantId(), incidentId, type, title, body));

        Tenant tenant = tenants.findById(incident.getTenantId()).orElseThrow();
        if (tenant.getWebhookUrl() != null) {
            jdbc.update("""
                    INSERT INTO webhook_deliveries (tenant_id, notification_id, event, title, url, payload, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                    """, tenant.getId(), saved.getId(), type, title, tenant.getWebhookUrl(),
                    payload(type, incident, serviceName, saved));
        }
    }

    @Transactional(readOnly = true)
    public NotificationList list(Long tenantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<NotificationView> items = notifications.findByTenantIdOrderByCreatedAtDescIdDesc(tenantId, Limit.of(safeLimit))
                .stream()
                .map(n -> new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getIncidentId(),
                        n.isRead(), n.getCreatedAt()))
                .toList();
        return new NotificationList(notifications.countByTenantIdAndReadFalse(tenantId), items);
    }

    @Transactional
    public void markRead(Long tenantId, Long id) {
        notifications.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Notification", id)).markRead();
    }

    @Transactional
    public void markAllRead(Long tenantId) {
        notifications.markAllRead(tenantId);
    }

    private String payload(String type, Incident incident, String serviceName, Notification notification) {
        Map<String, Object> incidentJson = new LinkedHashMap<>();
        incidentJson.put("id", incident.getId());
        incidentJson.put("title", incident.getTitle());
        incidentJson.put("severity", incident.getSeverity());
        incidentJson.put("status", incident.getStatus());
        incidentJson.put("service", serviceName);
        incidentJson.put("openedAt", incident.getOpenedAt());
        incidentJson.put("resolvedAt", incident.getResolvedAt());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", type);
        body.put("notificationId", notification.getId());
        body.put("occurredAt", notification.getCreatedAt());
        body.put("incident", incidentJson);
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
