package com.becho.saas.security;

import com.becho.saas.modules.platform.entity.ApiKey;
import com.becho.saas.modules.platform.service.ApiKeyService;
import com.becho.saas.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Authenticates third-party integrations via API key.
 *
 * <p>Accepts {@code X-API-Key: <key>} or {@code Authorization: ApiKey <key>}.
 * Verification is hash-only (the DB never holds a usable key), checks
 * status/expiry, binds the request to the key's store, and updates
 * last-used metadata. JWT authentication always takes precedence: this
 * filter never overwrites an existing authentication.
 *
 * <p>Scope mapping is conservative: a key grants {@code ROLE_APIKEY} plus
 * {@code ROLE_APIKEY_READ} when its scopes include read access and
 * {@code ROLE_APIKEY_WRITE} for write access. Keys with no parsable scopes
 * default to read-only. Endpoints opt in via {@code @PreAuthorize}; by
 * default API keys are denied by role checks.
 *
 * <p>The raw key is never logged.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

  private final ApiKeyService apiKeyService;

  public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      if (SecurityContextHolder.getContext().getAuthentication() == null) {
        String rawKey = extractKey(req);
        if (rawKey != null && !rawKey.isBlank()) {
          authenticate(rawKey.trim(), req);
        }
      }
    } catch (Exception e) {
      // Invalid/revoked/expired key -> request continues unauthenticated.
      log.debug("API key authentication failed for {} {}", req.getMethod(), req.getRequestURI());
    }
    chain.doFilter(req, res);
  }

  private String extractKey(HttpServletRequest req) {
    String header = req.getHeader("X-API-Key");
    if (header != null && !header.isBlank()) {
      return header;
    }
    String auth = req.getHeader("Authorization");
    if (auth != null && auth.startsWith("ApiKey ")) {
      return auth.substring(7);
    }
    return null;
  }

  private void authenticate(String rawKey, HttpServletRequest req) {
    ApiKey key = apiKeyService.verify(rawKey);
    if (key.getStore() == null || key.getStore().getId() == null) {
      return;
    }
    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_APIKEY"));
    if (canRead(key.getScopes())) {
      authorities.add(new SimpleGrantedAuthority("ROLE_APIKEY_READ"));
    }
    if (canWrite(key.getScopes())) {
      authorities.add(new SimpleGrantedAuthority("ROLE_APIKEY_WRITE"));
    }
    var authentication = new UsernamePasswordAuthenticationToken(
        "apikey:" + key.getKeyPrefix(), null, authorities);
    authentication.setDetails(key.getStore().getId());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    // Bind tenant scope to the key's store — never trust a client-supplied store id.
    TenantContext.set(key.getStore().getId(), null);
    log.debug("API key authenticated (prefix={}, store={}, uri={})",
        key.getKeyPrefix(), key.getStore().getId(), req.getRequestURI());
  }

  /** Null/unparsable scopes default to read-only (conservative). */
  private boolean canRead(String scopesJson) {
    if (scopesJson == null || scopesJson.isBlank()) {
      return true;
    }
    String s = scopesJson.toLowerCase();
    return s.contains("read") || s.contains("write") || s.contains("*");
  }

  private boolean canWrite(String scopesJson) {
    if (scopesJson == null || scopesJson.isBlank()) {
      return false;
    }
    String s = scopesJson.toLowerCase();
    return s.contains("write") || s.contains("*");
  }
}
