package com.pulseops.auth;

/**
 * The principal for dashboard requests, rebuilt from the JWT on every request (no session, no DB lookup).
 * Controllers take the tenant id from here and never from the URL or request body.
 */
public record AuthenticatedUser(Long userId, Long tenantId, String email, Role role) {
}
