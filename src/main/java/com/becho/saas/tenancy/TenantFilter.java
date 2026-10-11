package com.becho.saas.tenancy;

import com.becho.saas.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Resolves tenant scope per request. Order of precedence:
 * 1. JWT claims (store_id for staff, set at login)
 * 2. X-Store-Id header is NOT trusted — only used for public storefront
 *    resolution via verified domain mapping (StorefrontResolver).
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final StorefrontResolver storefrontResolver;

  public TenantFilter(JwtService jwtService, StorefrontResolver storefrontResolver) {
    this.jwtService = jwtService;
    this.storefrontResolver = storefrontResolver;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      Long storeId = null;
      Long userId = null;

      String auth = req.getHeader("Authorization");
      if (auth != null && auth.startsWith("Bearer ")) {
        String token = auth.substring(7).trim();
        if (!token.isEmpty()) {
          try {
            var claims = jwtService.parse(token);
            userId = Long.valueOf(claims.getSubject());
            Object sid = claims.get("store_id");
            if (sid != null) storeId = Long.valueOf(sid.toString());
          } catch (Exception ignored) {
            // Invalid/expired/malformed token -> continue as anonymous.
            // (The frontend sends "Authorization: Bearer " with an empty token
            // on logged-out requests; that must not 500.)
          }
        }
      }

      if (storeId == null && req.getRequestURI().startsWith("/api/storefront/")) {
        storeId = storefrontResolver.resolve(req.getServerName());
      }

      TenantContext.set(storeId, userId);
      chain.doFilter(req, res);
    } finally {
      TenantContext.clear();
    }
  }
}
