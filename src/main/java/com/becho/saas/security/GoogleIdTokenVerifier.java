package com.becho.saas.security;

import com.becho.saas.common.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies Google ID tokens server-side using Google's published JWKS
 * (https://www.googleapis.com/oauth2/v3/certs).
 *
 * <p>Checks: RSA-SHA256 signature against the current Google signing keys,
 * issuer (accounts.google.com), audience == our {@code GOOGLE_CLIENT_ID},
 * expiry, and that Google marks the email as verified. The frontend must send
 * the real Google-issued credential JWT ({@code idToken}) — accepting a bare
 * {@code {name, email}} from the client is an account-takeover hole and is
 * rejected.
 *
 * <p>Keys are cached for 1 hour and refreshed on an unknown {@code kid}.
 * If {@code GOOGLE_CLIENT_ID} is not set, Google sign-in is disabled and
 * every call fails with 503 instead of silently issuing tokens.
 */
@Component
public class GoogleIdTokenVerifier {

  private static final String CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
  private static final Duration CACHE_TTL = Duration.ofHours(1);

  private final String clientId;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  private volatile Map<String, PublicKey> keys = Map.of();
  private volatile long fetchedAt = 0L;

  public GoogleIdTokenVerifier(@Value("${becho.google.client-id:}") String clientId) {
    this.clientId = clientId == null ? "" : clientId.trim();
  }

  public boolean isConfigured() {
    return !clientId.isEmpty();
  }

  /** Verified Google identity: lowercase email + display name from the token claims. */
  public record GoogleUser(String email, String name) {}

  public GoogleUser verify(String idToken) {
    if (!isConfigured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
          "Google sign-in is not configured on this server");
    }
    if (idToken == null || idToken.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_ID_TOKEN",
          "Google ID token is required");
    }
    String kid;
    try {
      kid = extractKid(idToken);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_ID_TOKEN", "Malformed Google ID token");
    }
    PublicKey key = keys.get(kid);
    if (key == null) {
      refreshKeys();
      key = keys.get(kid);
    }
    if (key == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_ID_TOKEN",
          "Google ID token was not signed by a known Google key");
    }
    try {
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(idToken.trim())
          .getPayload(); // throws on bad signature / expiry / malformed

      String iss = claims.getIssuer();
      if (!"https://accounts.google.com".equals(iss) && !"accounts.google.com".equals(iss)) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_ID_TOKEN", "Invalid token issuer");
      }
      if (!audienceMatches(claims.get("aud"))) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_ID_TOKEN",
            "Token was not issued for this application");
      }
      String email = claims.get("email", String.class);
      Boolean emailVerified = claims.get("email_verified", Boolean.class);
      if (email == null || email.isBlank() || !Boolean.TRUE.equals(emailVerified)) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_VERIFIED",
            "Google has not verified this email address");
      }
      return new GoogleUser(email.toLowerCase().trim(), claims.get("name", String.class));
    } catch (ApiException e) {
      throw e;
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Google ID token has expired");
    } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_ID_TOKEN", "Invalid Google ID token");
    }
  }

  private boolean audienceMatches(Object aud) {
    if (aud instanceof String s) {
      return clientId.equals(s);
    }
    if (aud instanceof Collection<?> c) {
      return c.contains(clientId);
    }
    return false;
  }

  /** Reads the {@code kid} header without trusting it — only used to pick the verification key. */
  private String extractKid(String idToken) throws Exception {
    String[] parts = idToken.trim().split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException("not a JWT");
    }
    JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
    JsonNode kid = header.get("kid");
    if (kid == null || kid.asText().isBlank()) {
      throw new IllegalArgumentException("no kid");
    }
    return kid.asText();
  }

  private synchronized void refreshKeys() {
    // Double-check: another thread may have refreshed while we waited.
    if (System.currentTimeMillis() - fetchedAt < CACHE_TTL.toMillis() && !keys.isEmpty()) {
      return;
    }
    try {
      HttpRequest req = HttpRequest.newBuilder(URI.create(CERTS_URL))
          .timeout(Duration.ofSeconds(15))
          .GET()
          .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) {
        throw new IllegalStateException("Google certs returned HTTP " + res.statusCode());
      }
      JsonNode root = mapper.readTree(res.body());
      Map<String, PublicKey> fresh = new ConcurrentHashMap<>();
      for (JsonNode jwk : root.path("keys")) {
        if (!"RSA".equals(jwk.path("kty").asText())) {
          continue;
        }
        String kid = jwk.path("kid").asText(null);
        String n = jwk.path("n").asText(null);
        String e = jwk.path("e").asText(null);
        if (kid == null || n == null || e == null) {
          continue;
        }
        RSAPublicKeySpec spec = new RSAPublicKeySpec(
            new BigInteger(1, Base64.getUrlDecoder().decode(n)),
            new BigInteger(1, Base64.getUrlDecoder().decode(e)));
        fresh.put(kid, KeyFactory.getInstance("RSA").generatePublic(spec));
      }
      if (!fresh.isEmpty()) {
        keys = Map.copyOf(fresh);
        fetchedAt = System.currentTimeMillis();
      }
    } catch (Exception e) {
      // Keep the old cache (may be empty) — callers fail closed with BAD_ID_TOKEN.
      org.slf4j.LoggerFactory.getLogger(GoogleIdTokenVerifier.class)
          .warn("Failed to refresh Google signing keys: {}", e.toString());
    }
  }

  /** Test hook: bypass the network and verify against the given keys. */
  void injectKeysForTest(Map<String, PublicKey> testKeys) {
    this.keys = Map.copyOf(testKeys);
    this.fetchedAt = System.currentTimeMillis();
  }
}
