package com.pulseops.ingestor.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "telemetry_logs")
public class TelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which customer does this log belong to? (Crucial for B2B)
    private String tenantId;

    // The actual server metrics matching the Python Agent payload
    private String serviceName;
    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;  // <-- Added to catch Python data
    private String hostname;   // <-- Added to catch Python data
    private String status;

    private LocalDateTime timestamp = LocalDateTime.now();
}