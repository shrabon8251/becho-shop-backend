package com.becho.saas.modules.customers.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Customer lockout state.
 */
class CustomerLockoutTest {

  @Test
  void isLockedOut_falseWhenNoLock() {
    Customer c = new Customer();
    assertFalse(c.isLockedOut());
  }

  @Test
  void isLockedOut_trueWhenLockedInFuture() {
    Customer c = new Customer();
    c.setLockedUntil(LocalDateTime.now().plusMinutes(10));
    assertTrue(c.isLockedOut());
  }

  @Test
  void isLockedOut_falseWhenLockExpired() {
    Customer c = new Customer();
    c.setLockedUntil(LocalDateTime.now().minusMinutes(1));
    assertFalse(c.isLockedOut());
  }

  @Test
  void failedAttempts_defaultZero() {
    Customer c = new Customer();
    assertEquals(0, c.getFailedLoginAttempts());
  }
}
