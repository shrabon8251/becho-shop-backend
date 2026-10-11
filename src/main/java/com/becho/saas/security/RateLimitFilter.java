package com.becho.saas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiting backed by Redis (atomic INCR + EXPIRE).
 * Tightest on auth endpoints (/api/auth/**, /auth/**, shopper account auth)
 * for brute-force protection, looser elsewhere.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;

    /**
     * Only trust X-Forwarded-For when the app sits behind a known reverse proxy.
     * Otherwise clients could spoof the header and dodge rate limits.
     */
    @Value("${becho.security.trust-x-forwarded-for:false}")
    private boolean trustXForwardedFor;

    @Value("${becho.security.trusted-proxies:}")
    private String trustedProxies;

    public RateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * In-memory fallback buckets used only when Redis is unreachable.
     * Bounded per-instance fixed-window counters: auth stays throttled
     * (no brute-force free pass) but never 503s just because Redis is down.
     */
    private final ConcurrentHashMap<String, long[]> memoryBuckets = new ConcurrentHashMap<>();
    private static final int MEMORY_BUCKET_CAP = 100_000;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        int limit;
        int windowSeconds;
        if (path.equals("/api/auth/forgot-password") || path.equals("/auth/forgot-password")) {
            limit = 10; windowSeconds = 3600;        // 10 reset requests/hour per IP
        } else if (isAuthEndpoint(path)) {
            limit = 20; windowSeconds = 60;          // 20 auth attempts/min per IP
        } else if (isCheckoutEndpoint(path)) {
            limit = 60; windowSeconds = 60;          // 60 checkouts/intents per min per IP
        } else if (isFraudCheckEndpoint(path)) {
            limit = 30; windowSeconds = 60;          // 30 fraud checks/min per IP
        } else if (path.equals("/api/contact") || path.equals("/contact")) {
            limit = 5; windowSeconds = 3600;         // 5 contact msgs/hour per IP (spam vector)
        } else if (path.matches("/stores/[^/]+/account/phone/(send-otp|verify-otp)")) {
            limit = 10; windowSeconds = 3600;        // 10 OTP requests/hour per IP
        } else if (path.equals("/platform/landing")) {
            limit = 60; windowSeconds = 60;          // 60 landing views/min per IP
        } else if (path.equals("/platform/demo-themes")) {
            limit = 60; windowSeconds = 60;          // 60 demo theme views/min per IP
        } else if (path.startsWith("/api/storefront/")) {
            limit = 120; windowSeconds = 60;         // 120 public storefront reads/min per IP
        } else if (path.startsWith("/api/payments/callback/")) {
            limit = 120; windowSeconds = 60;         // gateways retry IPNs
        } else {
            limit = 300; windowSeconds = 60;         // general API
        }

        String ip = clientIp(req);
        String key = "rl:" + path.hashCode() + ":" + ip;
        if (!tryAcquire(key, limit, windowSeconds * 1000L)) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}}");
            return;
        }
        chain.doFilter(req, res);
    }

    /**
     * Fixed-window acquire: Redis first (atomic INCR + EXPIRE), in-memory
     * fallback when Redis is unreachable. Returns false when the limit is
     * exceeded.
     */
    private boolean tryAcquire(String key, int limit, long windowMillis) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) throw new IllegalStateException("Redis returned no rate-limit counter");
            if (count == 1) {
                redis.expire(key, Duration.ofMillis(windowMillis));
            }
            return count <= limit;
        } catch (Exception e) {
            return tryAcquireMemory(key, limit, windowMillis);
        }
    }

    private boolean tryAcquireMemory(String key, int limit, long windowMillis) {
        if (memoryBuckets.size() > MEMORY_BUCKET_CAP) memoryBuckets.clear();
        long now = System.currentTimeMillis();
        long[] bucket = memoryBuckets.compute(key, (k, v) -> {
            if (v == null || now - v[0] >= windowMillis) return new long[]{now, 1};
            v[1]++;
            return v;
        });
        return bucket[1] <= limit;
    }

    /**
     * Auth endpoints get the tight bucket (brute-force protection). Covers both
     * the /api/auth/** controllers and the frontend-compatible /auth/** routes,
     * plus shopper login/signup/google.
     */
    private boolean isAuthEndpoint(String path) {
        if (path.startsWith("/api/auth/") || path.startsWith("/auth/")) {
            return true;
        }
        // /stores/{id}/account/login | signup | google
        return path.matches("/stores/[^/]+/account/(login|signup|google)");
    }

    /** Public checkout endpoints: order creation + payment intents. */
    private boolean isCheckoutEndpoint(String path) {
        return path.matches("/stores/[^/]+/checkout/orders")
                || path.matches("/stores/[^/]+/payments/intents")
                || path.matches("/stores/[^/]+/stock-alerts");
    }

    /** Fraud checks are phone-number sensitive — tight bucket even for authed merchants. */
    private boolean isFraudCheckEndpoint(String path) {
        return path.matches("/stores/[^/]+/fraud-check");
    }

    private String clientIp(HttpServletRequest req) {
        if (trustXForwardedFor && fromTrustedProxy(req.getRemoteAddr())) {
            String fwd = req.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private boolean fromTrustedProxy(String remoteAddr) {
        if (trustedProxies == null || trustedProxies.isBlank()) return false;
        for (String p : trustedProxies.split(",")) {
            if (p.trim().equals(remoteAddr)) return true;
        }
        return false;
    }
}
