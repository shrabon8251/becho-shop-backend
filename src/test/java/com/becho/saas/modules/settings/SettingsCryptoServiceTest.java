package com.becho.saas.modules.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Settings encryption: courier creds + SMS apiKey encrypted at rest,
 * decrypt round-trips, idempotent, plaintext backwards-compatible.
 */
class SettingsCryptoServiceTest {

  private final SettingsCryptoService crypto =
      new SettingsCryptoService("test-settings-key-for-unit-tests");

  @Test
  void encryptsCourierCreds() {
    String json = """
        {"storeName":"X","couriers":{
          "steadfast":{"on":true,"creds":{"api_key":"AK123","secret_key":"SK456"}},
          "pathao":{"on":false,"creds":{"client_id":"plain"}}
        }}""";
    String encrypted = crypto.encryptSensitive(json);
    // Encrypted values have the ENC: prefix; non-sensitive untouched.
    assertTrue(encrypted.contains("ENC:"));
    assertTrue(encrypted.contains("\"storeName\":\"X\""));
    assertFalse(encrypted.contains("AK123"), "API key must not appear in plaintext");
    assertFalse(encrypted.contains("SK456"));
  }

  @Test
  void encryptsSmsApiKey() {
    String json = """
        {"notifications":{"sms":{"provider":"bulksmsbd","apiKey":"SMSKEY","senderId":"S"}}}""";
    String encrypted = crypto.encryptSensitive(json);
    assertFalse(encrypted.contains("SMSKEY"));
    assertTrue(encrypted.contains("\"provider\":\"bulksmsbd\""));
  }

  @Test
  void decryptRoundTrip() {
    String json = """
        {"couriers":{"steadfast":{"on":true,"creds":{"api_key":"AK123"}}}}""";
    String encrypted = crypto.encryptSensitive(json);
    // Extract the encrypted value and decrypt it.
    String encValue = encrypted.split("ENC:")[1].split("\"")[0];
    assertEquals("AK123", crypto.decryptValue("ENC:" + encValue));
  }

  @Test
  void plaintextPassesThrough() {
    // Pre-encryption data stays readable.
    assertEquals("plainkey", crypto.decryptValue("plainkey"));
    assertNull(crypto.decryptValue(null));
  }

  @Test
  void idempotent() {
    String json = """
        {"couriers":{"steadfast":{"on":true,"creds":{"api_key":"AK123"}}}}""";
    String once = crypto.encryptSensitive(json);
    String twice = crypto.encryptSensitive(once);
    // Second pass must not double-encrypt (ENC: prefix detected).
    assertEquals(once, twice);
  }

  @Test
  void disabledWithoutKey() {
    SettingsCryptoService off = new SettingsCryptoService("");
    assertFalse(off.isEnabled());
    String json = "{\"couriers\":{\"a\":{\"on\":true,\"creds\":{\"k\":\"v\"}}}}";
    assertEquals(json, off.encryptSensitive(json));
  }
}
