package com.becho.saas.modules.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-store email sender: settings.emailSender -> "Name <email>".
 * Invalid/missing -> null (caller falls back to platform default).
 */
class MailServiceSenderTest {

  @Test
  void resolvesNameAndEmail() {
    String json = "{\"storeName\":\"X\",\"emailSender\":{\"name\":\"My Store\",\"email\":\"orders@mystore.com\"}}";
    assertEquals("\"My Store\" <orders@mystore.com>",
        MailService.resolveSender(json));
  }

  @Test
  void resolvesBareEmailWhenNoName() {
    String json = "{\"emailSender\":{\"email\":\"orders@mystore.com\"}}";
    assertEquals("orders@mystore.com", MailService.resolveSender(json));
  }

  @Test
  void fallsBackOnMissingOrInvalid() {
    assertNull(MailService.resolveSender(null));
    assertNull(MailService.resolveSender(""));
    assertNull(MailService.resolveSender("{}"));
    assertNull(MailService.resolveSender("{\"emailSender\":{}}"));
    // Invalid email -> null (default used).
    assertNull(MailService.resolveSender(
        "{\"emailSender\":{\"email\":\"not-an-email\"}}"));
    assertNull(MailService.resolveSender(
        "{\"emailSender\":{\"email\":\"a@b\"}}"));
    // Malformed JSON -> null.
    assertNull(MailService.resolveSender("not-json"));
  }

  @Test
  void stripsQuotesFromName() {
    String json = "{\"emailSender\":{\"name\":\"A \\\"B\\\" Store\",\"email\":\"a@b.com\"}}";
    String resolved = MailService.resolveSender(json);
    assertNotNull(resolved);
    assertFalse(resolved.contains("\"B\""),
        "Quotes in display name must be stripped to avoid header injection");
  }
}
