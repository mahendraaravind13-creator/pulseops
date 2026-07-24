package com.pulseops.analyzer.service;

import org.springframework.stereotype.Service;

@Service
public class RemediationService {

    // Safe actions the agent is allowed to execute autonomously
    private static final java.util.List<String> APPROVED_KEYWORDS = java.util.List.of(
            "scale", "restart", "clear cache", "increase", "reduce",
            "horizontal", "pod", "memory", "cpu", "autoscal"
    );

    public boolean executeSafeAction(String recommendedAction) {
        System.out.println("   ⚙️  Evaluating action: " + recommendedAction);

        String actionLower = recommendedAction.toLowerCase();
        boolean isApproved = APPROVED_KEYWORDS.stream()
                .anyMatch(actionLower::contains);

        if (isApproved) {
            System.out.println("   🤖 Action is in approved safe-list.");
            System.out.println("   🚀 Executing: calling Kubernetes API to remediate...");
            // In production — real kubectl or K8s Java client call goes here
            System.out.println("   ✅ Remediation command sent successfully.");
            return true;
        } else {
            System.out.println("   ⚠️  Action not in approved safe-list.");
            System.out.println("   📢 Escalating to human on-call engineer.");
            return false;
        }
    }
}