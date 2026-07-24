package com.pulseops.ingestor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.ingestor.model.Tenant;
import com.pulseops.ingestor.model.TelemetryEvent;
import com.pulseops.ingestor.repository.TelemetryRepository;
import com.pulseops.ingestor.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/telemetry")
@CrossOrigin(origins = "*")
public class TelemetryController {

    @Autowired
    private TelemetryRepository repository;


    @Autowired
    private TenantRepository tenantRepository;


    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ── AUTHENTICATED ENDPOINT ────────────────────────────────
    @PostMapping
    public ResponseEntity<String> ingestLog(
            @RequestBody TelemetryEvent event,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (apiKey != null && !apiKey.isEmpty()) {
            Tenant tenant = tenantRepository.findByApiKey(apiKey).orElse(null);

            if (tenant == null) {
                System.err.println("❌ Rejected request — invalid API key: " + apiKey);
                return ResponseEntity.status(401).body("Invalid API key. Register at pulseops.io to get your key.");
            }

            // Override tenantId with the database value to prevent spoofing
            event.setTenantId(tenant.getTenantId());
            System.out.println("✅ Authenticated: " + tenant.getCompanyName()
                    + " | Service: " + event.getServiceName()
                    + " | CPU: " + event.getCpuUsage() + "%");
        } else {
            System.out.println("⚠️  No API key — accepting for local dev: " + event.getServiceName());
        }

        repository.save(event);

        try {
            String jsonMessage = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("metrics.clean", event.getTenantId(), jsonMessage);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Streaming failed: " + e.getMessage());
        }

        return ResponseEntity.ok("Metric received.");
    }

    // ── HEALTH CHECK ──────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<String> health(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        if (apiKey != null) {
            Tenant tenant = tenantRepository.findByApiKey(apiKey).orElse(null);
            if (tenant == null) {
                return ResponseEntity.status(401).body("Invalid API key.");
            }
            return ResponseEntity.ok("Connected. Monitoring " + tenant.getCompanyName() + " on PulseOps.");
        }
        return ResponseEntity.ok("PulseOps ingestor is running.");
    }
}