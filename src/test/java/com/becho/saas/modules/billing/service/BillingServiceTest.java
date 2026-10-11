package com.becho.saas.modules.billing.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.billing.entity.PromoCode;
import com.becho.saas.modules.billing.repository.PromoCodeRepository;
import com.becho.saas.modules.billing.repository.PromoRedemptionRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Server-side pricing: the backend recomputes every total — plan prices,
 * cycle discounts, and promo validation all happen here, never trusted
 * from the client.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

  @Mock PromoCodeRepository promoCodes;
  @Mock PromoRedemptionRepository redemptions;
  @Mock StoreRepository stores;

  BillingService billing;

  @BeforeEach
  void setUp() {
    billing = new BillingService(promoCodes, redemptions, stores);
  }

  private PromoCode activePromo() {
    PromoCode p = new PromoCode();
    p.setCode("BECHO10");
    p.setPercentOff(new BigDecimal("10.00"));
    p.setMaxUses(1_000_000);
    p.setUsedCount(0);
    p.setActive(true);
    return p;
  }

  @Test
  void yearlyCycleGets30PercentOff() {
    // Growth 799/mo × 12 = 9588; 30% off = 2876; total 6712
    Map<String, Object> q = billing.quote("Growth", "yearly", null);
    assertEquals(9588L, q.get("fullPrice"));
    assertEquals(2876L, q.get("cycleDiscount"));
    assertEquals(6712L, q.get("total"));
    assertEquals("BDT", q.get("currency"));
  }

  @Test
  void quarterlyCycleGets10PercentOff() {
    // Starter 299/mo × 3 = 897; 10% off = 90 (89.7 rounded); total 807
    Map<String, Object> q = billing.quote("Starter", "quarterly", null);
    assertEquals(897L, q.get("fullPrice"));
    assertEquals(90L, q.get("cycleDiscount"));
    assertEquals(807L, q.get("total"));
  }

  @Test
  void monthlyHasNoDiscount() {
    Map<String, Object> q = billing.quote("Pro", "monthly", null);
    assertEquals(1499L, q.get("total"));
    assertEquals(0L, q.get("cycleDiscount"));
  }

  @Test
  void promoAppliesOnCycleDiscountedSubtotal() {
    // Starter quarterly: subtotal 807; BECHO10 10% = 81 (80.7 rounded); total 726
    when(promoCodes.findByCode("BECHO10")).thenReturn(Optional.of(activePromo()));
    Map<String, Object> q = billing.quote("Starter", "quarterly", "becho10");
    assertEquals("BECHO10", q.get("promoCode"));
    assertEquals(81L, q.get("promoDiscount"));
    assertEquals(726L, q.get("total"));
  }

  @Test
  void unknownPromoIsRejected() {
    when(promoCodes.findByCode("FAKE")).thenReturn(Optional.empty());
    ApiException ex = assertThrows(ApiException.class,
        () -> billing.quote("Starter", "monthly", "FAKE"));
    assertEquals("PROMO_INVALID", ex.getCode());
  }

  @Test
  void inactivePromoIsRejected() {
    PromoCode p = activePromo();
    p.setActive(false);
    when(promoCodes.findByCode("BECHO10")).thenReturn(Optional.of(p));
    ApiException ex = assertThrows(ApiException.class,
        () -> billing.quote("Starter", "monthly", "BECHO10"));
    assertEquals("PROMO_INVALID", ex.getCode());
  }

  @Test
  void exhaustedPromoIsRejected() {
    PromoCode p = activePromo();
    p.setMaxUses(10);
    p.setUsedCount(10);
    when(promoCodes.findByCode("BECHO10")).thenReturn(Optional.of(p));
    ApiException ex = assertThrows(ApiException.class,
        () -> billing.quote("Starter", "monthly", "BECHO10"));
    assertEquals("PROMO_INVALID", ex.getCode());
  }

  @Test
  void unknownPlanIsRejected() {
    ApiException ex = assertThrows(ApiException.class,
        () -> billing.quote("Enterprise", "monthly", null));
    assertEquals("PLAN_INVALID", ex.getCode());
  }

  @Test
  void unknownCycleIsRejected() {
    ApiException ex = assertThrows(ApiException.class,
        () -> billing.quote("Starter", "decade", null));
    assertEquals("CYCLE_INVALID", ex.getCode());
  }
}
