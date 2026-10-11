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
        // BUG-41 fix: Growth 999/mo × 12 = 11988; 30% off = 3596 (3596.4 rounded); total 8392
        Map<String, Object> q = billing.quote("Growth", "yearly", null);
        assertEquals(11988L, q.get("fullPrice"));
        assertEquals(3596L, q.get("cycleDiscount"));
        assertEquals(8392L, q.get("total"));
        assertEquals("BDT", q.get("currency"));
    }

    @Test
    void quarterlyCycleGets10PercentOff() {
        // BUG-41 fix: Starter 399/mo × 3 = 1197; 10% off = 120 (119.7 rounded); total 1077
        Map<String, Object> q = billing.quote("Starter", "quarterly", null);
        assertEquals(1197L, q.get("fullPrice"));
        assertEquals(120L, q.get("cycleDiscount"));
        assertEquals(1077L, q.get("total"));
    }

    @Test
    void monthlyHasNoDiscount() {
        // BUG-41 fix: Pro 1999/mo
        Map<String, Object> q = billing.quote("Pro", "monthly", null);
        assertEquals(1999L, q.get("total"));
        assertEquals(0L, q.get("cycleDiscount"));
    }

    @Test
    void promoAppliesOnCycleDiscountedSubtotal() {
        // BUG-41 fix: Starter quarterly subtotal 1077; BECHO10 10% = 108 (107.7 rounded); total 969
        when(promoCodes.findByCode("BECHO10")).thenReturn(Optional.of(activePromo()));
        Map<String, Object> q = billing.quote("Starter", "quarterly", "becho10");
        assertEquals("BECHO10", q.get("promoCode"));
        assertEquals(108L, q.get("promoDiscount"));
        assertEquals(969L, q.get("total"));
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
