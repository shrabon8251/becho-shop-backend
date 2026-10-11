package com.becho.saas.modules.notifications;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Notification dispatch: channel toggles, template rendering, defaults.
 * (Actual mail/SMS sending is not unit-tested — needs real credentials.)
 */
class NotificationServiceTest {

  @Test
  void channelDefaultsToTrueWhenUnset() {
    assertTrue(NotificationService.channelOn("{}", "order_placed", "email"));
    assertTrue(NotificationService.channelOn(null, "order_placed", "sms"));
    assertTrue(NotificationService.channelOn("not-json", "order_placed", "email"));
  }

  @Test
  void respectsMerchantChannelToggle() {
    String json = """
        {"notifications":{"events":{
          "order_placed":{"email":false,"sms":true},
          "order_shipped":{"email":true,"sms":false}
        }}}""";
    assertFalse(NotificationService.channelOn(json, "order_placed", "email"));
    assertTrue(NotificationService.channelOn(json, "order_placed", "sms"));
    assertTrue(NotificationService.channelOn(json, "order_shipped", "email"));
    assertFalse(NotificationService.channelOn(json, "order_shipped", "sms"));
    // Unset event falls back to default true.
    assertTrue(NotificationService.channelOn(json, "order_delivered", "email"));
  }

  @Test
  void usesMerchantTemplateOverride() {
    String json = """
        {"notifications":{"templates":{
          "order_placed":{"subject":"Custom subject {{order_id}}"}
        }}}""";
    var tpl = NotificationService.template(json, "order_placed");
    assertEquals("Custom subject {{order_id}}", tpl.get("subject"));
    // Other keys fall back to defaults.
    assertTrue(tpl.get("emailBody").contains("Thanks for your order"));
  }

  @Test
  void fallsBackToDefaultTemplate() {
    var tpl = NotificationService.template("{}", "order_shipped");
    assertEquals("Your order {{order_id}} is on its way", tpl.get("subject"));
  }

  @Test
  void rendersVariables() {
    String out = NotificationService.render(
        "Hi {{name}}, order {{order_id}} ({{amount}}). {{unknown}} stays.",
        Map.of("name", "Rahim", "order_id", "#1001", "amount", "BDT 500.00"));
    assertEquals("Hi Rahim, order #1001 (BDT 500.00). {{unknown}} stays.", out);
  }

  @Test
  void extractsSmsConfig() {
    String json = """
        {"notifications":{"sms":{
          "provider":"bulksmsbd","apiKey":"KEY123","senderId":"MyStore"
        }}}""";
    var cfg = NotificationService.smsConfig(json);
    assertEquals("bulksmsbd", cfg.get("provider"));
    assertEquals("KEY123", cfg.get("apiKey"));
    assertEquals("MyStore", cfg.get("senderId"));
    assertTrue(NotificationService.smsConfig("{}").isEmpty());
  }

  @Test
  void smsNumberNormalization() {
    assertEquals("8801712345678", SmsService.toInternational("01712345678"));
    assertEquals("8801712345678", SmsService.toInternational("8801712345678"));
    assertEquals("8801712345678", SmsService.toInternational("+8801712345678"));
  }
}
