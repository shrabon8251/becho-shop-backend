package com.becho.saas.tenancy;

import com.becho.saas.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Empty/blank "Authorization: Bearer " must be treated as anonymous —
 * never produce an uncaught 500 from jwtService.parse("").
 */
class TenantFilterTest {

  private final JwtService jwt = mock(JwtService.class);
  private final StorefrontResolver resolver = mock(StorefrontResolver.class);
  private final TenantFilter filter = new TenantFilter(jwt, resolver);

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private HttpServletRequest requestWithAuth(String auth) throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getHeader("Authorization")).thenReturn(auth);
    when(req.getRequestURI()).thenReturn("/stores/1/products");
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    filter.doFilterInternal(req, res, chain);
    verify(chain).doFilter(req, res);
    return req;
  }

  @Test
  void emptyBearerTokenIsAnonymousNot500() throws Exception {
    requestWithAuth("Bearer ");
    verifyNoInteractions(jwt);
    assertNull(TenantContext.userId());
  }

  @Test
  void whitespaceOnlyBearerTokenIsAnonymous() throws Exception {
    requestWithAuth("Bearer    ");
    verifyNoInteractions(jwt);
    assertNull(TenantContext.userId());
  }

  @Test
  void missingAuthHeaderIsAnonymous() throws Exception {
    requestWithAuth(null);
    verifyNoInteractions(jwt);
  }

  @Test
  void malformedTokenIsAnonymousNot500() throws Exception {
    when(jwt.parse("garbage")).thenThrow(new RuntimeException("bad token"));
    requestWithAuth("Bearer garbage");
    assertNull(TenantContext.userId());
  }

  @Test
  void validTokenStillResolves() throws Exception {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn("42");
    when(claims.get("store_id")).thenReturn(7L);
    when(jwt.parse("good-token")).thenReturn(claims);
    // The filter clears TenantContext in its finally block, so capture
    // the resolved values while the chain is executing.
    Long[] captured = new Long[2];
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getHeader("Authorization")).thenReturn("Bearer good-token");
    when(req.getRequestURI()).thenReturn("/stores/1/products");
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    doAnswer(inv -> {
      captured[0] = TenantContext.userId();
      captured[1] = TenantContext.storeId();
      return null;
    }).when(chain).doFilter(req, res);
    filter.doFilterInternal(req, res, chain);
    assertEquals(42L, captured[0]);
    assertEquals(7L, captured[1]);
  }
}
