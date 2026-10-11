package com.becho.saas.storefront.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Public settings filter: sensitive keys stripped, safe keys kept.
 */
class PublicStorefrontControllerTest {

  @Test
  void publicSettings_stripsSensitiveKeys() {
    String raw = """
        {"storeName":"Demo","currency":"BDT",
         "brevo":{"apiKey":"secret123"},
         "emailSender":{"email":"a@b.com"},
         "couriers":{"steadfast":{"creds":{"apiKey":"k","secret":"s"},"enabled":true}},
         "notifications":{"sms":{"apiKey":"smskey","enabled":true},"email":{"enabled":true}}}""";
    String out = PublicStorefrontController.publicSettings(raw, 1L);
    assertTrue(out.contains("\"storeName\":\"Demo\""));
    assertTrue(out.contains("\"currency\":\"BDT\""));
    // Sensitive stripped.
    assertFalse(out.contains("secret123"));
    assertFalse(out.contains("smskey"));
    assertFalse(out.contains("\"brevo\""));
    assertFalse(out.contains("\"emailSender\""));
    assertFalse(out.contains("\"creds\""));
    // Non-sensitive kept.
    assertTrue(out.contains("\"enabled\":true"));
  }

  @Test
  void publicSettings_malformedFallsBack() {
    String out = PublicStorefrontController.publicSettings("not-json", 5L);
    assertTrue(out.contains("\"storeId\":\"5\""));
  }

  @Test
  void publicSettings_nullFallsBack() {
    String out = PublicStorefrontController.publicSettings(null, 5L);
    assertTrue(out.contains("\"currency\":\"BDT\""));
  }
}
