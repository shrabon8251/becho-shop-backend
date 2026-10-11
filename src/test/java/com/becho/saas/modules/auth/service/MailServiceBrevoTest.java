package com.becho.saas.modules.auth.service;

import com.becho.saas.modules.notifications.BrevoMailService;
import com.becho.saas.modules.settings.SettingsCryptoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Merchant Brevo key: settings.brevo.apiKey -> decrypted key or null.
 * No key configured -> null (caller skips; no platform fallback).
 */
class MailServiceBrevoTest {

  private MailService service() {
    return new MailService(new BrevoMailService(),
        new SettingsCryptoService("test-key"), "", "noreply@becho.com.bd");
  }

  @Test
  void extractsMerchantBrevoKey() {
    String json = "{\"brevo\":{\"apiKey\":\"xkeysib-123\"}}";
    assertEquals("xkeysib-123", service().merchantBrevoKey(json));
  }

  @Test
  void decryptsEncryptedKey() {
    SettingsCryptoService crypto = new SettingsCryptoService("test-key");
    String encrypted = crypto.encryptSensitive(
        "{\"brevo\":{\"apiKey\":\"xkeysib-abc\"}}");
    assertEquals("xkeysib-abc", service().merchantBrevoKey(encrypted));
  }

  @Test
  void returnsNullWhenMissing() {
    assertNull(service().merchantBrevoKey(null));
    assertNull(service().merchantBrevoKey(""));
    assertNull(service().merchantBrevoKey("{}"));
    assertNull(service().merchantBrevoKey("{\"brevo\":{}}"));
  }

  @Test
  void encryptsBrevoKeyAtRest() {
    SettingsCryptoService crypto = new SettingsCryptoService("test-key");
    String encrypted = crypto.encryptSensitive(
        "{\"brevo\":{\"apiKey\":\"xkeysib-secret\"}}");
    assertFalse(encrypted.contains("xkeysib-secret"),
        "Brevo API key must not appear in plaintext");
    assertTrue(encrypted.contains("ENC:"));
  }

  @Test
  void splitsSender() {
    assertArrayEquals(new String[]{"My Store", "orders@mystore.com"},
        MailService.splitSender("\"My Store\" <orders@mystore.com>"));
    assertArrayEquals(new String[]{"", "orders@mystore.com"},
        MailService.splitSender("orders@mystore.com"));
  }
}
