package com.pulseops.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class AgentReasoningService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper(); // The industry standard JSON parser

    public void executeStep1Diagnose(String telemetryData) {
        try {
            String prompt = "You are PulseOps, an autonomous L3 infrastructure agent. " +
                    "Analyze the following telemetry data: " + telemetryData + ". " +
                    "Determine the root cause of any anomalies. " +
                    "You MUST respond ONLY with a raw JSON object containing three exact keys: " +
                    "'root_cause' (string), 'confidence_score' (number 0-100), and 'recommended_action' (string). " +
                    "Do not include markdown formatting or backticks.";

            // Let Jackson build the Map so we know the structure is flawless
            Map<String, Object> requestMap = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            // Force the Map into a strict JSON string BEFORE giving it to WebClient
            String jsonPayload = objectMapper.writeValueAsString(requestMap);

            System.out.println("⏳ Agent is reasoning through telemetry data...");

            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(jsonPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println("✅ STEP 1 DIAGNOSIS COMPLETE (Structured JSON):");
            System.out.println(response);

        } catch (WebClientResponseException e) {
            // THE CRACKED BLACK BOX: This forces Google to tell us EXACTLY what is wrong
            System.err.println("❌ Google API Rejected the Request!");
            System.err.println("Status Code: " + e.getStatusCode());
            System.err.println("Hidden Google Error Message: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("❌ General Error: " + e.getMessage());
        }
    }
}