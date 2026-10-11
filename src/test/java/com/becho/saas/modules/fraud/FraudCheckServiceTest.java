package com.becho.saas.modules.fraud;

import com.becho.saas.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fraud check: phone normalization, risk thresholds, no-courier handling.
 * Courier API calls are not unit-tested (they need real credentials).
 */
class FraudCheckServiceTest {

  private final FraudCheckService service = new FraudCheckService();

  @Test
  void normalizesBangladeshiPhones() {
    assertEquals("01712345678", FraudCheckService.normalizePhone("01712345678"));
    assertEquals("01712345678", FraudCheckService.normalizePhone("+8801712345678"));
    assertEquals("01712345678", FraudCheckService.normalizePhone("8801712345678"));
    assertEquals("01712345678", FraudCheckService.normalizePhone("1712345678"));
    assertEquals("01712345678", FraudCheckService.normalizePhone("017-1234-5678"));
  }

  @Test
  void rejectsInvalidPhones() {
    assertThrows(ApiException.class, () -> FraudCheckService.normalizePhone(null));
    assertThrows(ApiException.class, () -> FraudCheckService.normalizePhone("12345"));
    assertThrows(ApiException.class, () -> FraudCheckService.normalizePhone("01212345678")); // bad prefix
    assertThrows(ApiException.class, () -> FraudCheckService.normalizePhone("0171234567"));  // too short
  }

  @Test
  void returnsNullWhenNoCourierConfigured() {
    assertNull(service.check("01712345678", Map.of()));
    // Incomplete creds are skipped too.
    assertNull(service.check("01712345678",
        Map.of("steadfast", Map.of("api_key", "only-one-key"))));
  }

  @Test
  void riskThresholds() {
    var svc = new FraudCheckService();
    // low: >= 80% delivered
    var low = svc.merge("01712345678",
        java.util.List.of(new FraudCheckService.CourierResult("steadfast", 10, 9, 1, 0)));
    assertEquals("low", low.risk());
    assertEquals(90, low.successRate());
    // medium: >= 50%
    var med = svc.merge("01712345678",
        java.util.List.of(new FraudCheckService.CourierResult("steadfast", 10, 6, 4, 0)));
    assertEquals("medium", med.risk());
    // high: < 50%
    var high = svc.merge("01712345678",
        java.util.List.of(new FraudCheckService.CourierResult("steadfast", 10, 3, 7, 0)));
    assertEquals("high", high.risk());
    // unknown: no parcels
    var unknown = svc.merge("01712345678",
        java.util.List.of(new FraudCheckService.CourierResult("steadfast", 0, 0, 0, 0)));
    assertEquals("unknown", unknown.risk());
    // merging sums across couriers
    var merged = svc.merge("01712345678", java.util.List.of(
        new FraudCheckService.CourierResult("steadfast", 10, 8, 2, 0),
        new FraudCheckService.CourierResult("pathao", 10, 8, 2, 0)));
    assertEquals(20, merged.totalParcels());
    assertEquals(16, merged.delivered());
    assertEquals(java.util.List.of("steadfast", "pathao"), merged.couriers());
  }
}
