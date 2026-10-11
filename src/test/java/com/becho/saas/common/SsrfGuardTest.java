package com.becho.saas.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSRF guard: blocks private/loopback/link-local destinations.
 */
class SsrfGuardTest {

  @Test
  void blocksLoopback() {
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("http://127.0.0.1/hook"));
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("http://localhost/hook"));
  }

  @Test
  void blocksPrivateRanges() {
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("http://10.0.0.5/hook"));
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("http://192.168.1.1/hook"));
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("http://172.16.5.4/hook"));
  }

  @Test
  void blocksLinkLocal() {
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("http://169.254.169.254/latest"));
  }

  @Test
  void blocksNonHttpScheme() {
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("ftp://example.com/hook"));
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("file:///etc/passwd"));
  }

  @Test
  void blocksUserinfo() {
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("http://user@example.com/hook"));
  }

  @Test
  void blocksBlankAndNull() {
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl(null));
    assertThrows(ApiException.class, () -> SsrfGuard.validateUrl("  "));
  }
}
