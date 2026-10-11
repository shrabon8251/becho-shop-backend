package com.becho.saas.modules.auth.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.Hashing;
import com.becho.saas.modules.auth.entity.EmailVerificationCode;
import com.becho.saas.modules.auth.entity.PhoneVerificationCode;
import com.becho.saas.modules.auth.repository.EmailVerificationCodeRepository;
import com.becho.saas.modules.auth.repository.PhoneVerificationCodeRepository;
import com.becho.saas.modules.tenancy.entity.User;
import com.becho.saas.modules.tenancy.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Email + phone verification with real codes.
 *
 * <p>Rules (both channels):
 * <ul>
 *   <li>6-digit code, only the SHA-256 hash is stored.</li>
 *   <li>15 min expiry (email), 10 min (phone).</li>
 *   <li>Max 5 wrong attempts per code, then it is dead.</li>
 *   <li>Max 5 sends per hour per user (abuse rate-limit).</li>
 *   <li>Sending a new code invalidates older unused ones.</li>
 *   <li>Comparison is constant-time.</li>
 * </ul>
 * Verification without the correct code is impossible — there is no
 * "just send the email/phone and get verified" path.
 */
@Service
public class VerificationService {

  private static final int MAX_ATTEMPTS = 5;
  private static final int MAX_SENDS_PER_HOUR = 5;

  private final EmailVerificationCodeRepository emailCodes;
  private final PhoneVerificationCodeRepository phoneCodes;
  private final UserRepository users;
  private final MailService mail;
  private final SmsService sms;

  public VerificationService(EmailVerificationCodeRepository emailCodes,
                             PhoneVerificationCodeRepository phoneCodes,
                             UserRepository users,
                             MailService mail,
                             SmsService sms) {
    this.emailCodes = emailCodes;
    this.phoneCodes = phoneCodes;
    this.users = users;
    this.mail = mail;
    this.sms = sms;
  }

  // ---------------------------------------------------------------- email

  @Transactional
  public void sendEmailCode(User user) {
    rateLimitCheck(emailCodes.countByUserIdAndCreatedAtAfter(
        user.getId(), LocalDateTime.now().minusHours(1)));
    // Invalidate older unused codes.
    emailCodes.findByUserIdAndUsedAtIsNull(user.getId()).forEach(c -> {
      c.setUsedAt(LocalDateTime.now());
      emailCodes.save(c);
    });
    String code = Hashing.numericCode(6);
    EmailVerificationCode rec = new EmailVerificationCode();
    rec.setUser(user);
    rec.setCodeHash(Hashing.sha256Hex(code));
    rec.setExpiresAt(LocalDateTime.now().plusMinutes(15));
    emailCodes.save(rec);
    mail.sendVerificationCode(user.getEmail(), code);
  }

  @Transactional
  public User verifyEmailCode(String email, String code) {
    assertCodeFormat(code);
    User user = users.findByEmail(email.toLowerCase().trim())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    if (user.isEmailVerified()) {
      return user; // idempotent
    }
    EmailVerificationCode rec = emailCodes
        .findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_CODE",
            "No verification code found. Request a new one."));
    if (rec.getAttempts() >= MAX_ATTEMPTS || rec.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALID",
          "This code is expired or has too many failed attempts. Request a new one.");
    }
    if (!Hashing.constantTimeEquals(rec.getCodeHash(), Hashing.sha256Hex(code.trim()))) {
      rec.setAttempts(rec.getAttempts() + 1);
      emailCodes.save(rec);
      throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_WRONG",
          "Wrong verification code (" + (MAX_ATTEMPTS - rec.getAttempts()) + " attempts left)");
    }
    rec.setUsedAt(LocalDateTime.now());
    emailCodes.save(rec);
    user.setEmailVerifiedAt(LocalDateTime.now());
    users.save(user);
    return user;
  }

  // ---------------------------------------------------------------- phone

  @Transactional
  public void sendPhoneOtp(User user) {
    if (user.getPhone() == null || user.getPhone().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NO_PHONE",
          "No phone number on this account");
    }
    rateLimitCheck(phoneCodes.countByUserIdAndCreatedAtAfter(
        user.getId(), LocalDateTime.now().minusHours(1)));
    phoneCodes.findByUserIdAndUsedAtIsNull(user.getId()).forEach(c -> {
      c.setUsedAt(LocalDateTime.now());
      phoneCodes.save(c);
    });
    String code = Hashing.numericCode(6);
    PhoneVerificationCode rec = new PhoneVerificationCode();
    rec.setUser(user);
    rec.setPhone(user.getPhone());
    rec.setCodeHash(Hashing.sha256Hex(code));
    rec.setExpiresAt(LocalDateTime.now().plusMinutes(10));
    phoneCodes.save(rec);
    sms.sendOtp(user.getPhone(), code);
  }

  @Transactional
  public User verifyPhoneOtp(String email, String code) {
    assertCodeFormat(code);
    User user = users.findByEmail(email.toLowerCase().trim())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    if (user.isPhoneVerified()) {
      return user; // idempotent
    }
    PhoneVerificationCode rec = phoneCodes
        .findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_CODE",
            "No OTP found. Request a new one."));
    if (rec.getAttempts() >= MAX_ATTEMPTS || rec.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALID",
          "This OTP is expired or has too many failed attempts. Request a new one.");
    }
    if (!Hashing.constantTimeEquals(rec.getCodeHash(), Hashing.sha256Hex(code.trim()))) {
      rec.setAttempts(rec.getAttempts() + 1);
      phoneCodes.save(rec);
      throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_WRONG",
          "Wrong OTP (" + (MAX_ATTEMPTS - rec.getAttempts()) + " attempts left)");
    }
    rec.setUsedAt(LocalDateTime.now());
    phoneCodes.save(rec);
    user.setPhoneVerifiedAt(LocalDateTime.now());
    users.save(user);
    return user;
  }

  // ---------------------------------------------------------------- shared

  private void assertCodeFormat(String supplied) {
    if (supplied == null || !supplied.trim().matches("\\d{6}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_REQUIRED",
          "A 6-digit verification code is required");
    }
  }

  private void rateLimitCheck(long sentLastHour) {
    if (sentLastHour >= MAX_SENDS_PER_HOUR) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
          "Too many codes sent. Try again later.");
    }
  }
}
