package com.pulseops.rules;

import com.pulseops.auth.AuthenticatedUser;
import com.pulseops.rules.RuleDtos.EnabledRequest;
import com.pulseops.rules.RuleDtos.RuleRequest;
import com.pulseops.rules.RuleDtos.RuleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<RuleResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ruleService.list(user.tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RuleRequest request) {
        return ruleService.create(user.tenantId(), request);
    }

    @PutMapping("/{id}")
    public RuleResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                               @Valid @RequestBody RuleRequest request) {
        return ruleService.update(user.tenantId(), id, request);
    }

    @PatchMapping("/{id}/enabled")
    public RuleResponse setEnabled(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                   @Valid @RequestBody EnabledRequest request) {
        return ruleService.setEnabled(user.tenantId(), id, request.enabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        ruleService.delete(user.tenantId(), id);
    }
}
