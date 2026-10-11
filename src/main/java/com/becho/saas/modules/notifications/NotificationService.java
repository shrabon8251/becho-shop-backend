package com.becho.saas.modules.notifications;

import com.becho.saas.modules.auth.service.MailService;
import com.becho.saas.modules.settings.SettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Central notification dispatch — the ONLY way store event notifications
 * go out (never client-triggered; see the ASOS push-channel hijack rule).
 *
 * <p>For each event the merchant controls channels via
 * {@code settings.notifications.events[eventId] = { email, sms }}:
 * <ul>
 *   <li><b>Email</b> goes out only when {@code email === true} AND
 *       {@code settings.emailSender.email} is set (no sender = no email —
 *       the platform address is never used for merchant mail).</li>
 *   <li><b>SMS</b> goes out only when {@code sms === true} AND
 *       {@code settings.notifications.sms = { provider, apiKey }} is
 *       configured.</li>
 * </ul>
 *
 * <p>Templates come from
 * {@code settings.notifications.templates[eventId] = { subject, emailBody, smsBody }};
 * missing keys fall back to the built-in defaults below. Variables use
 * {@code {{name}}} syntax.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final MailService mail;
  private final SmsService sms;
  private final SettingsCryptoService crypto;

  public NotificationService(MailService mail, SmsService sms,
                             SettingsCryptoService crypto) {
    this.mail = mail;
    this.sms = sms;
    this.crypto = crypto;
  }

  /** Built-in default templates (merchant overrides in settings). */
  private static final Map<String, Map<String, String>> DEFAULTS = Map.of(
      "order_placed", Map.of(
          "subject", "Order {{order_id}} confirmed — {{store}}",
          "emailBody", "Hi {{name}},\n\nThanks for your order {{order_id}} ({{amount}}) at {{store}}.\nWe'll notify you when it ships.",
          "smsBody", "{{store}}: Order {{order_id}} received ({{amount}}). Thank you!"),
      "order_shipped", Map.of(
          "subject", "Your order {{order_id}} is on its way",
          "emailBody", "Hi {{name}},\n\nYour order {{order_id}} has shipped!\nTrack it here: {{tracking_link}}",
          "smsBody", "{{store}}: Order {{order_id}} shipped. Track: {{tracking_link}}"),
      "order_delivered", Map.of(
          "subject", "Order {{order_id}} delivered",
          "emailBody", "Hi {{name}},\n\nYour order {{order_id}} has been delivered. Enjoy!",
          "smsBody", "{{store}}: Order {{order_id}} delivered. Thanks for shopping!"),
      "order_cancelled", Map.of(
          "subject", "Order {{order_id}} cancelled",
          "emailBody", "Hi {{name}},\n\nYour order {{order_id}} has been cancelled. Any payment will be refunded shortly.",
          "smsBody", "{{store}}: Order {{order_id}} cancelled."),
      "abandoned_checkout", Map.of(
          "subject", "You left something in your cart",
          "emailBody", "Hi {{name}},\n\nYour cart at {{store}} is waiting — complete your order before it sells out!",
          "smsBody", "{{store}}: Your cart is waiting! Complete your order now."),
      "low_stock", Map.of(
          "subject", "Low stock warning — {{store}}",
          "emailBody", "A product is running low on stock. Check your inventory.",
          "smsBody", "{{store}}: Low stock alert."),
      "new_order", Map.of(
          "subject", "New order {{order_id}} — {{store}}",
          "emailBody", "New order {{order_id}} ({{amount}}) from {{name}}.",
          "smsBody", "{{store}}: New order {{order_id}} ({{amount}})."));

  /**
   * Dispatches an event notification per the merchant's settings.
   *
   * @param storeSettingsJson raw settings JSON (may be null)
   * @param eventId           e.g. "order_placed"
   * @param vars              template variables (name, order_id, amount, ...)
   * @param customerEmail     recipient email (null skips email)
   * @param customerPhone     recipient phone (null skips SMS)
   */
  public void dispatch(String storeSettingsJson, String eventId,
                       Map<String, String> vars,
                       String customerEmail, String customerPhone) {
    boolean emailOn = channelOn(storeSettingsJson, eventId, "email");
    boolean smsOn = channelOn(storeSettingsJson, eventId, "sms");

    if (emailOn && customerEmail != null && !customerEmail.isBlank()) {
      String sender = MailService.resolveSender(storeSettingsJson);
      if (sender == null) {
        log.warn("Skipping {} email to {}: merchant emailSender not configured",
            eventId, customerEmail);
      } else {
        Map<String, String> tpl = template(storeSettingsJson, eventId);
        // Merchant's own SMTP; skipped if not configured (no platform fallback).
        mail.sendTemplated(customerEmail,
            render(tpl.get("subject"), vars),
            render(tpl.get("emailBody"), vars),
            sender, storeSettingsJson);
      }
    }

    if (smsOn && customerPhone != null && !customerPhone.isBlank()) {
      Map<String, String> smsCfg = smsConfig(storeSettingsJson);
      String provider = smsCfg.get("provider");
      String apiKey = smsCfg.get("apiKey");
      // Decrypt (plaintext passes through for pre-encryption data).
      if (apiKey != null && crypto != null) {
        apiKey = crypto.decryptValue(apiKey);
      }
      if (provider == null || provider.isBlank() || apiKey == null || apiKey.isBlank()) {
        log.warn("Skipping {} SMS to {}: SMS provider not configured",
            eventId, mask(customerPhone));
      } else {
        Map<String, String> tpl = template(storeSettingsJson, eventId);
        sms.send(provider, apiKey, smsCfg.get("senderId"),
            customerPhone, render(tpl.get("smsBody"), vars));
      }
    }
  }

  /** Merchant's channel toggle; defaults to true (frontend defaults). */
  static boolean channelOn(String settingsJson, String eventId, String channel) {
    try {
      JsonNode root = MAPPER.readTree(settingsJson);
      JsonNode v = root.path("notifications").path("events").path(eventId).path(channel);
      // Missing node -> default true (matches frontend NOTIF_EVENTS defaults).
      return v.isMissingNode() || v.asBoolean(true);
    } catch (Exception e) {
      return true;
    }
  }

  /** Merchant template override, else built-in default. */
  static Map<String, String> template(String settingsJson, String eventId) {
    Map<String, String> def = DEFAULTS.getOrDefault(eventId, Map.of(
        "subject", "Notification from {{store}}",
        "emailBody", "Hello {{name}},",
        "smsBody", "{{store}}: notification."));
    Map<String, String> out = new HashMap<>(def);
    try {
      JsonNode t = MAPPER.readTree(settingsJson)
          .path("notifications").path("templates").path(eventId);
      if (t.isObject()) {
        for (String k : new String[]{"subject", "emailBody", "smsBody"}) {
          JsonNode v = t.path(k);
          if (v.isTextual() && !v.asText().isBlank()) {
            out.put(k, v.asText());
          }
        }
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  static Map<String, String> smsConfig(String settingsJson) {
    Map<String, String> out = new HashMap<>();
    try {
      JsonNode s = MAPPER.readTree(settingsJson).path("notifications").path("sms");
      if (s.isObject()) {
        for (String k : new String[]{"provider", "apiKey", "senderId"}) {
          JsonNode v = s.path(k);
          if (v.isTextual()) out.put(k, v.asText(""));
        }
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  /** Replaces {{variables}}. Unknown variables are left as-is. */
  static String render(String tpl, Map<String, String> vars) {
    if (tpl == null) return "";
    String out = tpl;
    for (Map.Entry<String, String> e : vars.entrySet()) {
      out = out.replace("{{" + e.getKey() + "}}",
          e.getValue() == null ? "" : e.getValue());
    }
    return out;
  }

  private String mask(String phone) {
    return phone == null || phone.length() < 4 ? "****"
        : "****" + phone.substring(phone.length() - 4);
  }
}
