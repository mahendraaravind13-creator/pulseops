package com.pulseops.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * API keys are random secrets shown to the user once. Only their SHA-256 hash is stored.
 *
 * <p>Why SHA-256 and not BCrypt like passwords? The key has 192 bits of randomness, so brute force is impossible
 * even with a fast hash, and the lookup must be a direct indexed equality match on every ingest request.
 * BCrypt is deliberately slow and salted, which would make "find the tenant for this key" impossible without
 * scanning every tenant.
 */
public final class ApiKeys {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final int PREFIX_LENGTH = 10;

    private ApiKeys() {
    }

    public record Generated(String rawKey, String hash, String prefix) {
    }

    public static Generated generate() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String raw = "pk_" + HEX.formatHex(bytes);
        return new Generated(raw, hash(raw), raw.substring(0, PREFIX_LENGTH));
    }

    public static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
