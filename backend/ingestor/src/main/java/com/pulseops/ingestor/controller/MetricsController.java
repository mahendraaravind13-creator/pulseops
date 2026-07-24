package com.pulseops.ingestor.controller;

import com.pulseops.ingestor.model.TelemetryEvent;
import com.pulseops.ingestor.repository.TelemetryRepository;
import jakarta.persistence.metamodel.MapAttribute;
import org.hibernate.boot.archive.scan.internal.ScanResultImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {

    @Autowired
    private TelemetryRepository telemetryRepository;

    @GetMapping("/latest/tenant/{tenantId}")
    public ResponseEntity<List<Map<String, Object>>> getLatestMetrics(@PathVariable String tenantId) {

        List<TelemetryEvent> events = telemetryRepository.findLatestByTenantId(tenantId);

        // Convert the database logs into the format the React dashboard expects
        List<Map<String, Object>> response = events.stream().map(event -> {
            Map<String, Object> map = new HashMap<>();
            map.put("serviceName", event.getServiceName());
            map.put("status", event.getStatus());
            map.put("cpu", event.getCpuUsage());
            map.put("memory", event.getMemoryUsage());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}