package com.becho.saas.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Validates Bearer JWTs and installs the authentication into the security context. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String auth = req.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer ")) {
      try {
        Claims claims = jwtService.parse(auth.substring(7));
        if ("access".equals(claims.get("type"))) {
          String role = String.valueOf(claims.getOrDefault("role", "staff")).toUpperCase();
          var authorities = new ArrayList<SimpleGrantedAuthority>();
          authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
          // Platform admin is a user-level flag (users.platform_admin), not a staff role.
          if (Boolean.TRUE.equals(claims.get("platform_admin", Boolean.class))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
          }
          var authentication = new UsernamePasswordAuthenticationToken(
              claims.getSubject(), null, authorities);
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (Exception ignored) {
        // invalid/expired token -> request continues unauthenticated
      }
    }
    chain.doFilter(req, res);
  }
}
