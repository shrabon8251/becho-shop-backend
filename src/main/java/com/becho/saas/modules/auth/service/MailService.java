package com.becho.saas.modules.auth.service;

import com.becho.saas.modules.notifications.BrevoMailService;
import com.becho.saas.modules.settings.SettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Outbound email via Brevo REST API (HTTPS — works on Render, where
 * outbound SMTP ports are blocked).
 *
 * <p>Two kinds:
 * <ul>
 *   <li><b>Platform mail</b> (verification codes, password resets, contact
 *       notifications): sent via the platform's Brevo account
 *       ({@code BREVO_API_KEY} env, {@code becho.mail.from} sender).</li>
 *   <li><b>Merchant mail</b> (order notifications, stock alerts): sent via
 *       the merchant's OWN Brevo API key
 *       ({@code settings.brevo.apiKey}) with their
 *       {@code settings.emailSender} as the From address. No platform
 *       fallback — if the merchant hasn't configured a Brevo key, the mail
 *       is skipped (warn logged).</li>
 * </ul>
 *
 * <p>Sending is best-effort: failures are logged, never thrown.
 */
@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final BrevoMailService brevo;
  private final SettingsCryptoService crypto;
  private final String platformApiKey;
  private final String from;

  public MailService(BrevoMailService brevo,
                     SettingsCryptoService crypto,
                     @Value("${BREVO_API_KEY:}") String platformApiKey,
                     @Value("${becho.mail.from:noreply@becho.com.bd}") String from) {
    this.brevo = brevo;
    this.crypto = crypto;
    this.platformApiKey = platformApiKey;
    this.from = from;
  }

  // ---------------------------------------------------------------- platform

  public void sendVerificationCode(String toEmail, String code) {
    String subject = "Verify your Becho account";
    String body = "Your Becho verification code is: " + code + "\n\n"
        + "It expires in 15 minutes. If you didn't request this, ignore this email.";
    boolean sent = sendPlatform(toEmail, subject, body);
    if (!sent) {
      // Dev/demo fallback: log the code so the flow can be completed.
      log.warn("DEV-ONLY: email verification code for {} is {}", toEmail, code);
    }
  }

  public void sendPasswordResetCode(String toEmail, String code) {
    String subject = "Reset your Becho password";
    String body = "Your Becho password reset code is: " + code + "\n\n"
        + "It expires in 15 minutes. If you didn't request this, ignore this email.";
    boolean sent = sendPlatform(toEmail, subject, body);
    if (!sent) {
      log.warn("DEV-ONLY: password reset code for {} is {}", toEmail, code);
    }
  }

  public void sendContactNotification(String toEmail, String subject, String body) {
    if (!sendPlatform(toEmail, subject, body)) {
      log.warn("Contact message email could not be sent to {}", toEmail);
    }
  }

  private boolean sendPlatform(String to, String subject, String body) {
    if (platformApiKey == null || platformApiKey.isBlank()) {
      log.warn("BREVO_API_KEY not set; skipping platform email to {}", to);
      return false;
    }
    return brevo.send(platformApiKey, from, "Becho", to, subject, body);
  }

  // ---------------------------------------------------------------- merchant

  /**
   * Sends a templated merchant email via the merchant's OWN Brevo account.
   *
   * @param storeSettingsJson raw settings JSON (for brevo.apiKey + emailSender)
   * @param sender pre-validated sender address (see {@link #resolveSender})
   */
  public void sendTemplated(String toEmail, String subject, String body,
                            String sender, String storeSettingsJson) {
    if (sender == null || sender.isBlank()) {
      log.warn("Skipping templated email to {}: no merchant sender configured", toEmail);
      return;
    }
    String apiKey = merchantBrevoKey(storeSettingsJson);
    if (apiKey == null) {
      log.warn("Skipping templated email to {}: merchant Brevo API key not configured "
          + "(settings.brevo.apiKey). No platform fallback.", toEmail);
      return;
    }
    // sender is "Name <email>" or bare email — split for Brevo.
    String[] parts = splitSender(sender);
    if (!brevo.send(apiKey, parts[1], parts[0], toEmail, subject, body)) {
      log.warn("Templated email could not be sent to {}", toEmail);
    }
  }

  /** "Back in Stock" alert. Best-effort like all outbound mail. */
  public void sendStockAlert(String toEmail, String productTitle) {
    sendStockAlert(toEmail, productTitle, null);
  }

  /**
   * "Back in Stock" alert via the merchant's OWN Brevo account.
   *
   * <p>REQUIRED: {@code settings.brevo.apiKey} AND
   * {@code settings.emailSender.email}. If either is missing, the mail is
   * skipped (warn logged). No platform fallback.
   *
   * @param storeSettingsJson raw JSON from the store's settings column (may be null)
   */
  public void sendStockAlert(String toEmail, String productTitle, String storeSettingsJson) {
    String sender = resolveSender(storeSettingsJson);
    if (sender == null) {
      log.warn("Skipping stock alert to {}: merchant has not configured settings.emailSender.email",
          toEmail);
      return;
    }
    String subject = "Back in stock: " + productTitle;
    String body = "Good news!\n\n" + productTitle + " is back in stock.\n\n"
        + "Hurry — popular items sell out fast.";
    sendTemplated(toEmail, subject, body, sender, storeSettingsJson);
  }

  /**
   * Extracts and decrypts the merchant's Brevo API key from settings.
   * Returns null when not configured.
   */
  String merchantBrevoKey(String settingsJson) {
    if (settingsJson == null || settingsJson.isBlank()) {
      return null;
    }
    try {
      JsonNode keyNode = MAPPER.readTree(settingsJson).path("brevo").path("apiKey");
      if (!keyNode.isTextual()) {
        return null;
      }
      String key = keyNode.asText("");
      if (crypto != null) {
        key = crypto.decryptValue(key);
      }
      return (key == null || key.isBlank()) ? null : key;
    } catch (Exception e) {
      return null;
    }
  }

  /** Splits "Name <email>" into [name, email]; bare email -> ["", email]. */
  static String[] splitSender(String sender) {
    int lt = sender.lastIndexOf('<');
    int gt = sender.lastIndexOf('>');
    if (lt >= 0 && gt > lt) {
      String name = sender.substring(0, lt).trim().replace("\"", "");
      String email = sender.substring(lt + 1, gt).trim();
      return new String[]{name, email};
    }
    return new String[]{"", sender.trim()};
  }

  // ------------------------------------------------------------ sender parse

  /**
   * Resolves a per-store sender from {@code settings.emailSender}.
   * Returns a {@code "Name <email>"} string or a bare email, or null when
   * unset/invalid (caller must SKIP sending — never fall back to the
   * platform address for merchant mail).
   */
  public static String resolveSender(String settingsJson) {
    if (settingsJson == null || settingsJson.isBlank()) {
      return null;
    }
    try {
      // Lightweight parse — avoid a Jackson dependency here.
      String email = extractJsonString(settingsJson, "emailSender", "email");
      if (email == null || !isValidEmail(email)) {
        return null;
      }
      String name = extractJsonString(settingsJson, "emailSender", "name");
      if (name != null && !name.isBlank()) {
        // Quote the display name to handle commas etc.
        return "\"" + name.replace("\"", "") + "\" <" + email + ">";
      }
      return email;
    } catch (Exception ignored) {
      return null;
    }
  }

  /** Minimal nested JSON string extractor: obj -> key -> string value. */
  private static String extractJsonString(String json, String objKey, String key) {
    String objMarker = "\"" + objKey + "\"";
    int oi = json.indexOf(objMarker);
    if (oi < 0) return null;
    int brace = json.indexOf('{', oi + objMarker.length());
    if (brace < 0) return null;
    // Find matching close brace (no nesting expected in emailSender).
    int end = json.indexOf('}', brace);
    if (end < 0) return null;
    String obj = json.substring(brace, end + 1);
    String keyMarker = "\"" + key + "\"";
    int ki = obj.indexOf(keyMarker);
    if (ki < 0) return null;
    int colon = obj.indexOf(':', ki + keyMarker.length());
    if (colon < 0) return null;
    int q1 = obj.indexOf('"', colon);
    if (q1 < 0) return null;
    int q2 = obj.indexOf('"', q1 + 1);
    if (q2 < 0) return null;
    String val = obj.substring(q1 + 1, q2).trim();
    return val.isEmpty() ? null : val;
  }

  private static boolean isValidEmail(String email) {
    return email.matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
        && email.length() <= 254;
  }
}
