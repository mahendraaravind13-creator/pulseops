package com.pulseops.auth;

import com.pulseops.auth.AuthDtos.AuthResponse;
import com.pulseops.auth.AuthDtos.LoginRequest;
import com.pulseops.auth.AuthDtos.MeResponse;
import com.pulseops.auth.AuthDtos.RegisterRequest;
import com.pulseops.auth.AuthDtos.TenantView;
import com.pulseops.auth.AuthDtos.UserView;
import com.pulseops.common.ConflictException;
import com.pulseops.common.NotFoundException;
import com.pulseops.config.PulseOpsProperties;
import com.pulseops.rules.RuleService;
import com.pulseops.tenant.ApiKeys;
import com.pulseops.tenant.Tenant;
import com.pulseops.tenant.TenantRepository;
import com.pulseops.tenant.TenantService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final TenantRepository tenants;
    private final TenantService tenantService;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RuleService ruleService;
    private final PulseOpsProperties properties;

    public AuthService(TenantRepository tenants, TenantService tenantService, UserRepository users,
                       PasswordEncoder passwordEncoder, JwtService jwtService, RuleService ruleService,
                       PulseOpsProperties properties) {
        this.tenants = tenants;
        this.tenantService = tenantService;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.ruleService = ruleService;
        this.properties = properties;
    }

    /**
     * Tenant, owner, default rules and API key are created in one transaction: either the whole account exists
     * or none of it does. The email unique constraint is the final guard against two concurrent sign-ups.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        ApiKeys.Generated apiKey = ApiKeys.generate();
        Tenant tenant = tenants.save(new Tenant(request.companyName().trim(), uniqueSlug(request.companyName()),
                apiKey, properties.monitoring().defaultRateLimitPerMinute()));
        User owner = users.save(new User(tenant.getId(), email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), Role.OWNER));
        ruleService.createDefaultRules(tenant.getId());
        return new AuthResponse(jwtService.issue(owner), UserView.of(owner), TenantView.of(tenant), apiKey.rawKey());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        Tenant tenant = tenantService.get(user.getTenantId());
        return new AuthResponse(jwtService.issue(user), UserView.of(user), TenantView.of(tenant), null);
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedUser principal) {
        User user = users.findById(principal.userId()).orElseThrow(() -> new NotFoundException("User", principal.userId()));
        return new MeResponse(UserView.of(user), TenantView.of(tenantService.get(user.getTenantId())));
    }

    private String uniqueSlug(String companyName) {
        String base = companyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "tenant";
        }
        base = base.length() > 60 ? base.substring(0, 60) : base;
        String slug = base;
        while (tenants.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }
}
