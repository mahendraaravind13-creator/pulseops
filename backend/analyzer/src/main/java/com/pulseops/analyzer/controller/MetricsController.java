package com.pulseops.analyzer.controller;

import com.pulseops.analyzer.model.Incident;
import com.pulseops.analyzer.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {

    @Autowired
    private IncidentRepository incidentRepository;

    @GetMapping("/latest/tenant/{tenantId}")
    public ResponseEntity<List<Map<String, Object>>> getLatestMetrics(@PathVariable String tenantId) {

        // Added 'my-first-app' so your Python agent shows up on the dashboard!
        String[] coreServices = {
                "my-first-app",
                "payment-gateway",
                "orders-service",
                "auth-service",
                "inventory-db"
        };

        // Fetch all incidents, filter by TENANT, and check for active issues
        List<Incident> activeIncidents = incidentRepository.findAll().stream()
                .filter(i -> tenantId.equals(i.getTenantId()))
                .filter(i -> i.getStatus() == Incident.IncidentStatus.OPEN
                        || i.getStatus() == Incident.IncidentStatus.ACKNOWLEDGED
                        || i.getStatus() == Incident.IncidentStatus.ESCALATED)
                .toList();

        List<Map<String, Object>> serviceHealthList = new ArrayList<>();

        for (String service : coreServices) {
            // Check if this specific service has an active incident
            boolean isCritical = activeIncidents.stream()
                    .anyMatch(inc -> inc.getServiceName().equals(service));

            serviceHealthList.add(Map.of(
                    "serviceName", service,
                    "status", isCritical ? "CRITICAL" : "HEALTHY",
                    // Generate some fake "live" telemetry numbers for the UI
                    "cpu", isCritical ? (85 + Math.random() * 14) : (10 + Math.random() * 30),
                    "memory", isCritical ? (90 + Math.random() * 8) : (30 + Math.random() * 20)
            ));
        }

        return ResponseEntity.ok(serviceHealthList);
    }
}