package com.becho.saas.security;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.ApiKey;
import com.becho.saas.modules.platform.service.ApiKeyService;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * API-key authentication: valid keys authenticate with the key's store
 * binding; invalid/revoked/expired keys stay anonymous; JWT auth is never
 * overwritten.
 */
class ApiKeyAuthFilterTest {

  private final ApiKeyService service = mock(ApiKeyService.class);
  private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(service);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  private void runFilter(String apiKeyHeader, String authHeader) throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getHeader("X-API-Key")).thenReturn(apiKeyHeader);
    when(req.getHeader("Authorization")).thenReturn(authHeader);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/stores/99/products");
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    filter.doFilterInternal(req, res, chain);
    verify(chain).doFilter(req, res);
  }

  private ApiKey keyForStore(long storeId, String scopes) {
    Store store = new Store();
    try {
      var idField = store.getClass().getSuperclass().getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(store, storeId);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    ApiKey k = new ApiKey();
    k.setStore(store);
    k.setKeyPrefix("becho_abc123");
    k.setScopes(scopes);
    return k;
  }

  @Test
  void validKeyAuthenticatesWithKeyStoreBinding() throws Exception {
    // The key belongs to store 7, but the URL says 99: the KEY wins.
    when(service.verify("becho_valid")).thenReturn(keyForStore(7L, "[\"read\"]"));
    runFilter("becho_valid", null);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth);
    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_APIKEY")));
    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_APIKEY_READ")));
    assertFalse(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_APIKEY_WRITE")));
    assertEquals(7L, TenantContext.storeId());
  }

  @Test
  void writeScopeGrantsWriteRole() throws Exception {
    when(service.verify("becho_w")).thenReturn(keyForStore(7L, "[\"read\",\"write\"]"));
    runFilter("becho_w", null);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_APIKEY_WRITE")));
  }

  @Test
  void noScopesDefaultsToReadOnly() throws Exception {
    when(service.verify("becho_r")).thenReturn(keyForStore(7L, null));
    runFilter("becho_r", null);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_APIKEY_READ")));
    assertFalse(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_APIKEY_WRITE")));
  }

  @Test
  void invalidKeyStaysAnonymous() throws Exception {
    when(service.verify("becho_bad"))
        .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "no"));
    runFilter("becho_bad", null);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    assertNull(TenantContext.storeId());
  }

  @Test
  void missingKeyHeaderStaysAnonymous() throws Exception {
    runFilter(null, null);
    verifyNoInteractions(service);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void existingJwtAuthIsNeverOverwritten() throws Exception {
    var jwtAuth = new UsernamePasswordAuthenticationToken("42", null,
        List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    SecurityContextHolder.getContext().setAuthentication(jwtAuth);
    TenantContext.set(5L, 42L);
    when(service.verify("becho_valid")).thenReturn(keyForStore(7L, "[\"read\"]"));
    runFilter("becho_valid", null);
    // JWT auth untouched; API key service never consulted.
    assertSame(jwtAuth, SecurityContextHolder.getContext().getAuthentication());
    assertEquals(5L, TenantContext.storeId());
    verifyNoInteractions(service);
  }

  @Test
  void authorizationApiKeySchemeAlsoAccepted() throws Exception {
    when(service.verify("becho_valid")).thenReturn(keyForStore(7L, "[\"read\"]"));
    runFilter(null, "ApiKey becho_valid");
    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    assertEquals(7L, TenantContext.storeId());
  }
}
