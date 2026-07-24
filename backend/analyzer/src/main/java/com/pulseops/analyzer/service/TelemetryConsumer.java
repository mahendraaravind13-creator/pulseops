package com.pulseops.analyzer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelemetryConsumer {

    @Autowired
    private AgentReasoningService agent;

    private final ObjectMapper mapper = new ObjectMapper();

    // Tracks the last time the agent ran for each service
    // Prevents the same service from triggering Gemini
    // more than once every 5 minutes
    private final Map<String, Long> lastAgentRun = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes

    @KafkaListener(topics = "metrics.clean", groupId = "pulseops-ai-final")
    public void consume(String message) {
        try {
            JsonNode event    = mapper.readTree(message);
            String serviceName = event.path("serviceName").asText();
            String tenantId    = event.path("tenantId").asText();
            double cpu         = event.path("cpuUsage").asDouble();
            double memory      = event.path("memoryUsage").asDouble();
            String status      = event.path("status").asText("HEALTHY");

            // Only act on CRITICAL events
            if (!"CRITICAL".equals(status)) {
                System.out.println("✅ Healthy metric from "
                        + serviceName + " — no action needed.");
                return;
            }

            // Cooldown check — prevent hammering Gemini API
            String key = tenantId + ":" + serviceName;
            long now   = System.currentTimeMillis();
            Long last  = lastAgentRun.get(key);

            if (last != null && (now - last) < COOLDOWN_MS) {
                long secondsLeft = (COOLDOWN_MS - (now - last)) / 1000;
                System.out.println("⏳ Cooldown active for " + serviceName
                        + " — " + secondsLeft + "s remaining. Skipping.");
                return;
            }

            // Update the last run time before calling agent
            lastAgentRun.put(key, now);

            System.out.println("\n🚨 ANOMALY DETECTED — " + serviceName
                    + " | CPU: " + cpu + "% | MEM: " + memory
                    + "% | STATUS: " + status);
            System.out.println("   Passing to Agentic Loop...");

            agent.runAgenticLoop(message, tenantId, serviceName);

        } catch (Exception e) {
            System.err.println("❌ Failed to parse Kafka message: " + e.getMessage());
        }
    }
}