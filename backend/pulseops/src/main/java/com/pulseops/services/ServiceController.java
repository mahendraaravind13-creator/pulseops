package com.pulseops.services;

import com.pulseops.auth.AuthenticatedUser;
import com.pulseops.services.ServiceHealthService.MetricSeries;
import com.pulseops.services.ServiceHealthService.ServiceHealth;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServiceHealthService health;

    public ServiceController(ServiceHealthService health) {
        this.health = health;
    }

    @GetMapping
    public List<ServiceHealth> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return health.list(user.tenantId());
    }

    @GetMapping("/{id}")
    public ServiceHealth get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return health.get(user.tenantId(), id);
    }

    @GetMapping("/{id}/metrics")
    public MetricSeries metrics(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                @RequestParam(defaultValue = "1h") String range) {
        return health.metrics(user.tenantId(), id, range);
    }
}
