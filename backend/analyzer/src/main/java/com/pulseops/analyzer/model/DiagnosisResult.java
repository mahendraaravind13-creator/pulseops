package com.pulseops.analyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DiagnosisResult {

    @JsonProperty("root_cause")
    private String rootCause;

    @JsonProperty("confidence_score")
    private int confidenceScore;

    @JsonProperty("recommended_action")
    private String recommendedAction;
}