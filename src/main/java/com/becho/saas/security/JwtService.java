package com.becho.saas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stateless JWT issuance/parsing. Secrets come from env, never code. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    /** Placeholder values that must never be accepted as a real secret. */
    private static final Set<String> BLOCKED_SECRETS = Set.of(
            "change-me",
            "change-me-to-a-256-bit-secret-in-production",
            "secret",
            "password",
            "test-secret");

    public JwtService(
            @Value("${becho.jwt.secret}") String secret,
            @Value("${becho.jwt.access-ttl-minutes:30}") long accessTtlMinutes,
            @Value("${becho.jwt.refresh-ttl-days:14}") long refreshTtlDays) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "becho.jwt.secret is not set. Set the JWT_SECRET environment variable "
                            + "to a random 256-bit (32+ byte) secret before starting the app.");
        }
        if (BLOCKED_SECRETS.contains(secret.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "becho.jwt.secret uses a placeholder value. Set JWT_SECRET to a real random secret.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("becho.jwt.secret must be at least 256 bits (32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlMinutes * 60;
        this.refreshTtlSeconds = refreshTtlDays * 24 * 3600;
    }

    public String accessToken(Long userId, Long storeId, String role) {
        return accessToken(userId, storeId, role, false);
    }

    public String accessToken(Long userId, Long storeId, String role, boolean platformAdmin) {
        // storeId/role may be null for users that haven't created a store yet
        // (onboarding flow) — Map.of rejects nulls, so build the map manually.
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        if (storeId != null) claims.put("store_id", storeId);
        if (role != null) claims.put("role", role);
        claims.put("platform_admin", platformAdmin);
        claims.put("type", "access");
        return build(userId, claims, accessTtlSeconds);
    }

    /**
     * Refresh token bound to a persisted token id (jti) so it can be
     * rotated and revoked. The id is assigned by the caller after persisting.
     */
    public String refreshToken(Long userId, Long tokenId) {
        return build(userId, Map.of("type", "refresh", "jti", String.valueOf(tokenId)), refreshTtlSeconds);
    }

    /** Backwards-compat: unbound refresh token (no rotation). Prefer refreshToken(userId, tokenId). */
    public String refreshToken(Long userId) {
        return build(userId, Map.of("type", "refresh"), refreshTtlSeconds);
    }

    public long getRefreshTtlSeconds() { return refreshTtlSeconds; }

    private String build(Long userId, Map<String, Object> claims, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
