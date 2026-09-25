package com.pulseops.ai;

import java.util.List;

/** Prompt text in one place, so it can be read and reviewed separately from the plumbing. */
final class AnalysisPrompts {

    private AnalysisPrompts() {
    }

    record PastIncident(String title, String openedAt, String rootCause, String resolutionNote) {
    }

    static String diagnosis(String serviceName, String hostname, String title, String condition, double peak,
                            String openedAt, String metricsCsv, List<PastIncident> history) {
        StringBuilder p = new StringBuilder();
        p.append("""
                You are assisting an on-call engineer. An alert rule fired for a monitored service.
                Suggest the most probable root cause and concrete next steps. You cannot run commands;
                an engineer will verify and act. Be specific to the data; if the data is inconclusive, say so
                and lower your confidence.

                """);
        p.append("Service: ").append(serviceName).append(hostname == null ? "" : " (host " + hostname + ")").append('\n');
        p.append("Incident: ").append(title).append('\n');
        p.append("Rule condition: ").append(condition).append('\n');
        p.append("Worst value seen: ").append(String.format("%.1f", peak)).append('\n');
        p.append("Opened at (UTC): ").append(openedAt).append("\n\n");
        p.append("Metrics around the incident, 30-second averages (UTC):\n");
        p.append("time,cpu_pct,memory_pct,disk_pct,latency_ms,error_rate_pct\n").append(metricsCsv).append('\n');
        if (history.isEmpty()) {
            p.append("No earlier resolved incidents of this kind for this service.\n");
        } else {
            p.append("Earlier resolved incidents of the same kind on this service (most recent first):\n");
            for (PastIncident h : history) {
                p.append("- ").append(h.openedAt()).append(": ").append(h.title());
                if (h.rootCause() != null) p.append(" | suggested cause: ").append(h.rootCause());
                if (h.resolutionNote() != null) p.append(" | engineer's note: ").append(h.resolutionNote());
                p.append('\n');
            }
        }
        p.append("""

                Respond with a JSON object only, exactly this shape:
                {"root_cause": "<one or two sentences>",
                 "confidence": <integer 0-100, how well the data supports the root cause>,
                 "suggested_actions": ["<short imperative step>", "... 2 to 4 items"]}
                """);
        return p.toString();
    }

    static String postmortem(String serviceName, String title, String openedAt, String resolvedAt, String duration,
                             String rootCause, String resolution, String timeline) {
        return """
                Write a short, blameless incident post-mortem draft for an engineering team.
                Use plain text with these headings on their own lines: Summary, Impact, Probable cause,
                Resolution, Follow-up actions. Keep it under 250 words. Do not invent facts that are not below;
                where information is missing, say what the team should confirm.

                Service: %s
                Incident: %s
                Opened (UTC): %s
                Resolved (UTC): %s
                Duration: %s
                AI-suggested root cause (unverified): %s
                How it was resolved: %s

                Timeline:
                %s
                """.formatted(serviceName, title, openedAt, resolvedAt, duration,
                rootCause == null ? "none" : rootCause, resolution, timeline);
    }
}
