package com.becho.saas.modules.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Encrypts sensitive values inside store settings JSON (courier API keys,
 * SMS provider keys) before they hit the database.
 *
 * <p>AES-256-GCM with a key derived from the {@code BECHO_SETTINGS_KEY}
 * env var (SHA-256). Encrypted values are stored as
 * {@code "ENC:<base64(iv+ciphertext)>"} — the prefix lets us distinguish
 * already-encrypted values from plaintext (for migrating old data).
 *
 * <p>If {@code BECHO_SETTINGS_KEY} is blank, encryption is DISABLED and
 * values are stored as-is (with a loud startup warning) — Md must set it
 * in production.
 */
@Service
public class SettingsCryptoService {

  private static final Logger log = LoggerFactory.getLogger(SettingsCryptoService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PREFIX = "ENC:";
  private static final SecureRandom RANDOM = new SecureRandom();

  /** settings.couriers.<id>.creds.<key> and notifications.sms.apiKey */
  private static final String[][] SENSITIVE_PATHS = {
      // handled specially: couriers.*.creds.* (wildcard)
  };

  private final SecretKeySpec key;
  private final boolean enabled;

  public SettingsCryptoService(
      @Value("${becho.settings.key:}") String settingsKey) {
    if (settingsKey == null || settingsKey.isBlank()) {
      log.warn("SECURITY: BECHO_SETTINGS_KEY is not set — sensitive settings "
          + "values will be stored in PLAINTEXT. Set it in production!");
      this.key = null;
      this.enabled = false;
    } else {
      try {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(settingsKey.getBytes(StandardCharsets.UTF_8));
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.enabled = true;
      } catch (Exception e) {
        throw new IllegalStateException("Failed to init settings crypto", e);
      }
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Encrypts sensitive values in a settings JSON document:
   * {@code couriers.*.creds.*}, {@code notifications.sms.apiKey},
   * and {@code brevo.apiKey}. Already-encrypted values are left alone
   * (idempotent).
   */
  public String encryptSensitive(String settingsJson) {
    if (!enabled || settingsJson == null || settingsJson.isBlank()) {
      return settingsJson;
    }
    try {
      ObjectNode root = (ObjectNode) MAPPER.readTree(settingsJson);
      // couriers.<id>.creds.<key>
      JsonNode couriers = root.path("couriers");
      if (couriers.isObject()) {
        for (Map.Entry<String, JsonNode> ce : couriers.properties()) {
          JsonNode creds = ce.getValue().path("creds");
          if (creds.isObject()) {
            ObjectNode credsObj = (ObjectNode) creds;
            for (Map.Entry<String, JsonNode> ke : creds.properties()) {
              if (ke.getValue().isTextual()) {
                String v = ke.getValue().asText();
                if (!v.isBlank() && !v.startsWith(PREFIX)) {
                  credsObj.put(ke.getKey(), encrypt(v));
                }
              }
            }
          }
        }
      }
      // notifications.sms.apiKey
      JsonNode smsNode = root.path("notifications").path("sms");
      if (smsNode.isObject()) {
        JsonNode smsKey = smsNode.path("apiKey");
        if (smsKey.isTextual()) {
          String v = smsKey.asText();
          if (!v.isBlank() && !v.startsWith(PREFIX)) {
            ((ObjectNode) smsNode).put("apiKey", encrypt(v));
          }
        }
      }
      // brevo.apiKey (merchant's own Brevo account for sending mail)
      JsonNode brevoNode = root.path("brevo");
      if (brevoNode.isObject()) {
        JsonNode keyNode = brevoNode.path("apiKey");
        if (keyNode.isTextual()) {
          String v = keyNode.asText();
          if (!v.isBlank() && !v.startsWith(PREFIX)) {
            ((ObjectNode) brevoNode).put("apiKey", encrypt(v));
          }
        }
      }
      return MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      log.warn("Failed to encrypt settings; storing as-is: {}", e.toString());
      return settingsJson;
    }
  }

  /**
   * Decrypts sensitive values. Plaintext values (no prefix) are returned
   * as-is for backwards compatibility with pre-encryption data.
   */
  public String decryptValue(String stored) {
    if (stored == null || !stored.startsWith(PREFIX) || !enabled) {
      return stored;
    }
    try {
      byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      byte[] iv = new byte[12];
      System.arraycopy(raw, 0, iv, 0, 12);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] plain = cipher.doFinal(raw, 12, raw.length - 12);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("Failed to decrypt a settings value");
      return null;
    }
  }

  private String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[12];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[12 + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, 12);
      System.arraycopy(cipherText, 0, combined, 12, cipherText.length);
      return PREFIX + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Settings encryption failed", e);
    }
  }
}
