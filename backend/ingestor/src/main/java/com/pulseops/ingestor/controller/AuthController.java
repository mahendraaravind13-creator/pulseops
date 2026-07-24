package com.pulseops.ingestor.controller;

import com.pulseops.ingestor.model.Tenant;
import com.pulseops.ingestor.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private TenantRepository tenantRepository;

    // ── REGISTER ──────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email       = body.get("email");
        String password    = body.get("password");
        String companyName = body.get("companyName");

        if (email == null || password == null || companyName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email, password, and companyName are required."));
        }

        if (tenantRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "An account with this email already exists."));
        }

        String tenantId = companyName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (tenantRepository.existsByTenantId(tenantId)) {
            tenantId = tenantId + "-" + UUID.randomUUID().toString().substring(0, 4);
        }

        String apiKey = "pk_" + UUID.randomUUID().toString().replace("-", "");

        Tenant tenant = new Tenant();
        tenant.setEmail(email);
        tenant.setPasswordHash(password); // Note: Use BCrypt in real prod
        tenant.setCompanyName(companyName);
        tenant.setTenantId(tenantId);
        tenant.setApiKey(apiKey);
        tenant.setSubscriptionStatus("FREE");
        tenantRepository.save(tenant);

        System.out.println("✅ New tenant registered: " + companyName + " | tenantId: " + tenantId);

        return ResponseEntity.ok(Map.of(
                "message",     "Account created successfully.",
                "tenantId",    tenantId,
                "companyName", companyName,
                "apiKey",      apiKey,
                "email",       email
        ));
    }

    // ── LOGIN ─────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and password are required."));
        }

        Tenant tenant = tenantRepository.findByEmail(email).orElse(null);

        if (tenant == null || !tenant.getPasswordHash().equals(password)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password."));
        }

        System.out.println("✅ Login: " + tenant.getCompanyName());

        return ResponseEntity.ok(Map.of(
                "tenantId",           tenant.getTenantId(),
                "companyName",        tenant.getCompanyName(),
                "email",              tenant.getEmail(),
                "subscriptionStatus", tenant.getSubscriptionStatus()
        ));
    }
}