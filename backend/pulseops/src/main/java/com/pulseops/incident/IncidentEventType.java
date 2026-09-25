package com.pulseops.incident;

public enum IncidentEventType {
    OPENED,
    ACKNOWLEDGED,
    RESOLVED,
    AUTO_RESOLVED,
    NOTE,
    ANALYSIS_COMPLETED,
    ANALYSIS_FAILED,
    POSTMORTEM_READY
}
