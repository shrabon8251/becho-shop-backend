package com.becho.saas.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JWT secret must never silently fall back to a known/weak value:
 * the app must fail fast at startup instead of booting insecure.
 */
class JwtServiceSecretTest {

  private static final String VALID = "a-valid-256-bit-secret-0123456789abcdef";

  @Test
  void blankSecretFailsFast() {
    assertThrows(IllegalStateException.class, () -> new JwtService("", 30, 14));
    assertThrows(IllegalStateException.class, () -> new JwtService("   ", 30, 14));
    assertThrows(IllegalStateException.class, () -> new JwtService(null, 30, 14));
  }

  @Test
  void placeholderSecretsAreRejected() {
    assertThrows(IllegalStateException.class,
        () -> new JwtService("change-me-to-a-256-bit-secret-in-production", 30, 14));
    assertThrows(IllegalStateException.class,
        () -> new JwtService("test-secret", 30, 14));
  }

  @Test
  void shortSecretIsRejected() {
    assertThrows(IllegalStateException.class,
        () -> new JwtService("too-short-secret", 30, 14));
  }

  @Test
  void validSecretBoots() {
    // Construction must succeed (no fail-fast); token round-trip is
    // covered by the Spring integration tests.
    assertDoesNotThrow(() -> new JwtService(VALID, 30, 14));
  }
}
