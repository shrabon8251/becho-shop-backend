package com.becho.saas.storefront.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Courier credential extraction from store settings JSON.
 * Credentials stay server-side; only enabled couriers are returned.
 */
class StoreFraudCheckControllerTest {

  @Test
  void extractsEnabledCourierCreds() {
    String settings = """
        {"storeName":"Test","couriers":{
          "steadfast":{"on":true,"creds":{"api_key":"AK","secret_key":"SK"}},
          "pathao":{"on":false,"creds":{"client_id":"x"}},
          "redx":{"on":true,"creds":{}}
        }}""";
    var crypto = new com.becho.saas.modules.settings.SettingsCryptoService("test-key-12345");
    var creds = StoreFraudCheckController.extractCourierCreds(settings, crypto);
    assertEquals(2, creds.size());
    assertEquals("AK", creds.get("steadfast").get("api_key"));
    assertEquals("SK", creds.get("steadfast").get("secret_key"));
    assertTrue(creds.get("redx").isEmpty());
    assertFalse(creds.containsKey("pathao"));
  }

  @Test
  void handlesMissingOrMalformedSettings() {
    assertTrue(StoreFraudCheckController.extractCourierCreds(null, null).isEmpty());
    assertTrue(StoreFraudCheckController.extractCourierCreds("", null).isEmpty());
    assertTrue(StoreFraudCheckController.extractCourierCreds("{}", null).isEmpty());
    assertTrue(StoreFraudCheckController.extractCourierCreds("not-json", null).isEmpty());
    assertTrue(StoreFraudCheckController
        .extractCourierCreds("{\"couriers\":\"oops\"}", null).isEmpty());
  }
}
