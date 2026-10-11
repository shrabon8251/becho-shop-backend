package com.becho.saas.modules.auth.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.Hashing;
import com.becho.saas.modules.auth.entity.EmailVerificationCode;
import com.becho.saas.modules.auth.entity.PhoneVerificationCode;
import com.becho.saas.modules.auth.repository.EmailVerificationCodeRepository;
import com.becho.saas.modules.auth.repository.PhoneVerificationCodeRepository;
import com.becho.saas.modules.tenancy.entity.User;
import com.becho.saas.modules.tenancy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verification codes: wrong code never verifies, attempts are counted,
 * expired/exhausted codes are rejected, correct code verifies exactly once.
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

  @Mock EmailVerificationCodeRepository emailCodes;
  @Mock PhoneVerificationCodeRepository phoneCodes;
  @Mock UserRepository users;
  @Mock MailService mail;
  @Mock SmsService sms;

  VerificationService service;
  User user;

  @BeforeEach
  void setUp() {
    service = new VerificationService(emailCodes, phoneCodes, users, mail, sms);
    user = new User();
    user.setEmail("u@example.com");
    user.setFullName("U");
    // id is normally assigned by JPA; set via reflection for the test
    try {
      var f = com.becho.saas.common.BaseEntity.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(user, 42L);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private EmailVerificationCode emailCode(String plainCode, int attempts, LocalDateTime expires) {
    EmailVerificationCode rec = new EmailVerificationCode();
    rec.setUser(user);
    rec.setCodeHash(Hashing.sha256Hex(plainCode));
    rec.setAttempts(attempts);
    rec.setExpiresAt(expires);
    return rec;
  }

  @Test
  void correctCodeVerifiesEmail() {
    when(users.findByEmail("u@example.com")).thenReturn(Optional.of(user));
    when(emailCodes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(42L))
        .thenReturn(Optional.of(emailCode("123456", 0, LocalDateTime.now().plusMinutes(5))));

    User out = service.verifyEmailCode("u@example.com", "123456");

    assertTrue(out.isEmailVerified());
    verify(emailCodes).save(any(EmailVerificationCode.class));
    verify(users).save(user);
  }

  @Test
  void emailCannotBeVerifiedWithoutCode() {
    // The old vulnerable behavior: verify-email with only an email. Must fail.
    // The code-format check runs before any DB lookup.
    ApiException ex = assertThrows(ApiException.class,
        () -> service.verifyEmailCode("u@example.com", null));
    assertEquals("CODE_REQUIRED", ex.getCode());
    verify(users, never()).save(any());
  }

  @Test
  void wrongCodeDoesNotVerifyAndCountsAttempt() {
    when(users.findByEmail("u@example.com")).thenReturn(Optional.of(user));
    EmailVerificationCode rec = emailCode("123456", 0, LocalDateTime.now().plusMinutes(5));
    when(emailCodes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(42L))
        .thenReturn(Optional.of(rec));

    ApiException ex = assertThrows(ApiException.class,
        () -> service.verifyEmailCode("u@example.com", "999999"));
    assertEquals("CODE_WRONG", ex.getCode());
    assertEquals(1, rec.getAttempts());
    assertFalse(user.isEmailVerified());
  }

  @Test
  void exhaustedAttemptsRejectCode() {
    when(users.findByEmail("u@example.com")).thenReturn(Optional.of(user));
    when(emailCodes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(42L))
        .thenReturn(Optional.of(emailCode("123456", 5, LocalDateTime.now().plusMinutes(5))));

    ApiException ex = assertThrows(ApiException.class,
        () -> service.verifyEmailCode("u@example.com", "123456"));
    assertEquals("CODE_INVALID", ex.getCode());
    assertFalse(user.isEmailVerified());
  }

  @Test
  void expiredCodeIsRejected() {
    when(users.findByEmail("u@example.com")).thenReturn(Optional.of(user));
    when(emailCodes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(42L))
        .thenReturn(Optional.of(emailCode("123456", 0, LocalDateTime.now().minusMinutes(1))));

    ApiException ex = assertThrows(ApiException.class,
        () -> service.verifyEmailCode("u@example.com", "123456"));
    assertEquals("CODE_INVALID", ex.getCode());
    assertFalse(user.isEmailVerified());
  }

  @Test
  void sendEmailCodeInvalidatesPreviousOnes() {
    when(emailCodes.countByUserIdAndCreatedAtAfter(eq(42L), any())).thenReturn(0L);
    EmailVerificationCode old = emailCode("111111", 0, LocalDateTime.now().plusMinutes(5));
    when(emailCodes.findByUserIdAndUsedAtIsNull(42L)).thenReturn(List.of(old));

    service.sendEmailCode(user);

    assertNotNull(old.getUsedAt());
    verify(emailCodes, times(2)).save(any(EmailVerificationCode.class)); // old invalidated + new
    verify(mail).sendVerificationCode(eq("u@example.com"), any());
  }

  @Test
  void sendEmailCodeRateLimited() {
    when(emailCodes.countByUserIdAndCreatedAtAfter(eq(42L), any())).thenReturn(5L);

    ApiException ex = assertThrows(ApiException.class, () -> service.sendEmailCode(user));
    assertEquals("RATE_LIMITED", ex.getCode());
    verify(mail, never()).sendVerificationCode(any(), any());
  }

  @Test
  void correctOtpVerifiesPhone() {
    user.setPhone("01700000000");
    when(users.findByEmail("u@example.com")).thenReturn(Optional.of(user));
    PhoneVerificationCode rec = new PhoneVerificationCode();
    rec.setUser(user);
    rec.setPhone("01700000000");
    rec.setCodeHash(Hashing.sha256Hex("654321"));
    rec.setAttempts(0);
    rec.setExpiresAt(LocalDateTime.now().plusMinutes(5));
    when(phoneCodes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(42L))
        .thenReturn(Optional.of(rec));

    User out = service.verifyPhoneOtp("u@example.com", "654321");

    assertTrue(out.isPhoneVerified());
  }

  @Test
  void phoneCannotBeVerifiedWithoutOtp() {
    // The old vulnerable behavior: verify-phone with only an email. Must fail.
    // The code-format check runs before any DB lookup.
    ApiException ex = assertThrows(ApiException.class,
        () -> service.verifyPhoneOtp("u@example.com", null));
    assertEquals("CODE_REQUIRED", ex.getCode());
    assertFalse(user.isPhoneVerified());
    verify(users, never()).save(any());
  }
}
