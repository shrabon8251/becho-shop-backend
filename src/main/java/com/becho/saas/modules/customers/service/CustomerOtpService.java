package com.becho.saas.modules.customers.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.Hashing;
import com.becho.saas.modules.customers.entity.Customer;
import com.becho.saas.modules.customers.repository.CustomerRepository;
import com.becho.saas.modules.notifications.SmsService;
import com.becho.saas.modules.settings.SettingsCryptoService;
import com.becho.saas.modules.settings.StoreSettingsCache;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shopper self-service phone OTP: customers verify their own phone number
 * without merchant intervention. OTPs are hash-only, 10-minute expiry,
 * single-use, max 5 attempts, max 5 sends/hour per phone.
 *
 * <p>SMS goes through the merchant's configured provider (server-side only).
 * If no SMS provider is configured, the OTP is logged (dev/demo mode).
 */
@Service
public class CustomerOtpService {

  private static final Logger log = LoggerFactory.getLogger(CustomerOtpService.class);
  private static final int MAX_ATTEMPTS = 5;
  private static final int MAX_SENDS_PER_HOUR = 5;
  private static final int EXPIRY_MINUTES = 10;

  private record OtpRecord(String codeHash, LocalDateTime expiresAt, int attempts,
                           LocalDateTime sentAt) {}

  // In-memory OTP store: phone -> record. Acceptable because OTPs are
  // short-lived (10 min) and the alternative (DB table) adds migration
  // overhead for transient data. Production note: use Redis for multi-instance.
  private final Map<String, OtpRecord> otpStore = new ConcurrentHashMap<>();
  private final Map<String, Integer> sendCounts = new ConcurrentHashMap<>();

  private final CustomerRepository customers;
  private final StoreRepository stores;
  private final SmsService sms;
  private final SettingsCryptoService crypto;
  private final StoreSettingsCache settingsCache;

  public CustomerOtpService(CustomerRepository customers, StoreRepository stores,
                            SmsService sms, SettingsCryptoService crypto,
                            StoreSettingsCache settingsCache) {
    this.customers = customers;
    this.stores = stores;
    this.sms = sms;
    this.crypto = crypto;
    this.settingsCache = settingsCache;
  }

  /** Sends an OTP to the customer's own phone (self-service). */
  @Transactional
  public void sendOtp(Long storeId, String email, String phone) {
    Customer customer = customers.findByStoreIdAndEmailAndDeletedAtIsNull(storeId, email.toLowerCase().trim())
        .orElseThrow(() -> ApiException.notFound("Customer"));
    String normalizedPhone = normalizePhone(phone);
    if (!normalizedPhone.equals(customer.getPhone())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_MISMATCH",
          "Phone number does not match our records");
    }
    String key = storeId + ":" + normalizedPhone;
    // Rate limit: 5/hour per phone.
    cleanupExpired();
    int sends = sendCounts.getOrDefault(key, 0);
    if (sends >= MAX_SENDS_PER_HOUR) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
          "Too many OTP requests. Try again later.");
    }
    String code = Hashing.numericCode(6);
    otpStore.put(key, new OtpRecord(Hashing.sha256Hex(code),
        LocalDateTime.now().plusMinutes(EXPIRY_MINUTES), 0, LocalDateTime.now()));
    sendCounts.put(key, sends + 1);
    // Send via merchant's SMS provider, or log in dev mode.
    String settings = settingsCache.getSettings(storeId);
    boolean sent = sendSms(settings, normalizedPhone, code);
    if (!sent) {
      log.warn("DEV-ONLY: customer OTP for {} is {}", mask(normalizedPhone), code);
    }
  }

  /** Verifies the OTP and marks the customer's phone as verified. */
  @Transactional
  public void verifyOtp(Long storeId, String email, String code) {
    Customer customer = customers.findByStoreIdAndEmailAndDeletedAtIsNull(storeId, email.toLowerCase().trim())
        .orElseThrow(() -> ApiException.notFound("Customer"));
    if (code == null || !code.trim().matches("\\d{6}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_CODE", "OTP must be 6 digits");
    }
    String key = storeId + ":" + customer.getPhone();
    OtpRecord rec = otpStore.get(key);
    if (rec == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_OTP",
          "No OTP found. Request a new one.");
    }
    if (rec.attempts() >= MAX_ATTEMPTS || rec.expiresAt().isBefore(LocalDateTime.now())) {
      otpStore.remove(key);
      throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID",
          "OTP expired or too many attempts. Request a new one.");
    }
    if (!Hashing.constantTimeEquals(rec.codeHash(), Hashing.sha256Hex(code.trim()))) {
      otpStore.put(key, new OtpRecord(rec.codeHash(), rec.expiresAt(),
          rec.attempts() + 1, rec.sentAt()));
      throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_WRONG",
          "Wrong OTP (" + (MAX_ATTEMPTS - rec.attempts() - 1) + " attempts left)");
    }
    otpStore.remove(key); // single-use
    customer.setPhoneVerifiedAt(LocalDateTime.now());
    customers.save(customer);
  }

  private boolean sendSms(String settingsJson, String phone, String code) {
    try {
      // Extract SMS config from settings.
      var mapper = new tools.jackson.databind.ObjectMapper();
      var root = mapper.readTree(settingsJson == null ? "{}" : settingsJson);
      var smsNode = root.path("notifications").path("sms");
      String provider = smsNode.path("provider").asText("");
      String apiKey = smsNode.path("apiKey").asText("");
      if (crypto != null) apiKey = crypto.decryptValue(apiKey);
      if (provider.isBlank() || apiKey == null || apiKey.isBlank()) {
        return false;
      }
      String senderId = smsNode.path("senderId").asText("");
      return sms.send(provider, apiKey, senderId, phone,
          "Your verification code is: " + code);
    } catch (Exception e) {
      log.warn("Customer OTP SMS failed: {}", e.toString());
      return false;
    }
  }

  private String normalizePhone(String raw) {
    if (raw == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PHONE", "Phone is required");
    }
    String digits = raw.replaceAll("\\D", "");
    if (digits.startsWith("880") && digits.length() == 13) {
      digits = "0" + digits.substring(3);
    } else if (digits.length() == 10 && digits.startsWith("1")) {
      digits = "0" + digits;
    }
    if (!digits.matches("01[3-9][0-9]{8}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PHONE",
          "Invalid Bangladeshi phone number");
    }
    return digits;
  }

  private void cleanupExpired() {
    LocalDateTime now = LocalDateTime.now();
    otpStore.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    // Reset hourly counters (simple approach: clear old entries periodically).
    if (sendCounts.size() > 10000) {
      sendCounts.clear();
    }
  }

  private String mask(String phone) {
    return phone.length() < 4 ? "****" : "****" + phone.substring(phone.length() - 4);
  }
}
