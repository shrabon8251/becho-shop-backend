package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.auth.service.AuthService;
import com.becho.saas.modules.auth.service.PasswordResetService;
import com.becho.saas.modules.auth.service.VerificationService;
import com.becho.saas.modules.auth.web.AuthDtos.LoginRequest;
import com.becho.saas.modules.auth.web.AuthDtos.RegisterRequest;
import com.becho.saas.modules.tenancy.entity.User;
import com.becho.saas.modules.tenancy.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Frontend-compatible auth endpoints: POST /auth/login, /auth/signup,
 * /auth/google, /auth/verify-email, /auth/verify-phone.
 * Returns the frontend's User shape directly (with a `token` field carrying
 * the JWT for subsequent requests).
 */
@RestController
@RequestMapping("/auth")
public class StorefrontAuthController {

  private final AuthService authService;
  private final VerificationService verificationService;
  private final PasswordResetService passwordResetService;
  private final UserRepository users;

  public StorefrontAuthController(AuthService authService, VerificationService verificationService,
                                  PasswordResetService passwordResetService, UserRepository users) {
    this.authService = authService;
    this.verificationService = verificationService;
    this.passwordResetService = passwordResetService;
    this.users = users;
  }

  public record FrontendLoginRequest(
      @jakarta.validation.constraints.Email String email, String password) {}

  public record SignupRequest(String name,
      @jakarta.validation.constraints.Email String email, String password, String phone,
      java.time.LocalDateTime termsAcceptedAt) {}

  /**
   * Google sign-in. The client MUST send the real Google-issued ID token
   * (Google Identity Services credential). It is verified server-side
   * (signature, issuer, audience, expiry); a bare {name, email} is rejected.
   */
  public record GoogleRequest(String idToken) {}

  public record VerifyRequest(String email) {}

  /** Email/phone code verification: the 6-digit code is MANDATORY. */
  public record CodeVerifyRequest(
      @jakarta.validation.constraints.Email String email, String code) {}

  public record EmailOnlyRequest(
      @jakarta.validation.constraints.Email String email) {}

  public record FrontendUser(String id, String name, String email, String role,
      String staffRole, String storeId, String token,
      Boolean emailVerified, String phone, Boolean phoneVerified) {}

  @PostMapping("/login")
  public FrontendUser login(@Valid @RequestBody FrontendLoginRequest req) {
    var tokens = authService.login(new LoginRequest(req.email(), req.password(), null));
    User user = users.findById(tokens.userId()).orElseThrow();
    return toFrontendUser(user, tokens.storeId(), tokens.role(), tokens.accessToken());
  }

  @PostMapping("/signup")
  public FrontendUser signup(@Valid @RequestBody SignupRequest req) {
    String storeName = (req.name() != null ? req.name().trim() : "My") + "'s Store";
    var reg = authService.register(new RegisterRequest(req.name(), req.email(), req.password(),
        storeName, req.termsAcceptedAt()));
    User user = users.findById(reg.userId()).orElseThrow();
    if (req.phone() != null && !req.phone().isBlank()) {
      user.setPhone(req.phone().trim());
      users.save(user);
    }
    var tokens = authService.issueTokensForUser(user.getId());
    return toFrontendUser(user, tokens.storeId(), tokens.role(), tokens.accessToken());
  }

  @PostMapping("/google")
  public FrontendUser google(@RequestBody GoogleRequest req) {
    var tokens = authService.loginWithGoogleIdToken(req.idToken());
    User user = users.findById(tokens.userId()).orElseThrow();
    return toFrontendUser(user, tokens.storeId(), tokens.role(), tokens.accessToken());
  }

  /**
   * Verify email with the 6-digit code sent at signup. The code is mandatory —
   * sending only the email never verifies the account.
   */
  @PostMapping("/verify-email")
  public FrontendUser verifyEmail(@Valid @RequestBody CodeVerifyRequest req) {
    User user = verificationService.verifyEmailCode(req.email(), req.code());
    var tokens = authService.issueTokensForUser(user.getId());
    return toFrontendUser(user, tokens.storeId(), tokens.role(), tokens.accessToken());
  }

  /** Resend the email verification code (rate-limited, enumeration-safe). */
  @PostMapping("/verify-email/resend")
  public java.util.Map<String, Object> resendEmailCode(@Valid @RequestBody EmailOnlyRequest req) {
    // Enumeration-safe: same response whether or not the user exists.
    users.findByEmail(req.email().toLowerCase().trim())
        .ifPresent(verificationService::sendEmailCode);
    return java.util.Map.of("sent", true);
  }

  /**
   * Verify phone with the OTP sent to the user's phone number. The OTP is
   * mandatory — sending only the email never verifies the phone.
   */
  @PostMapping("/verify-phone")
  public FrontendUser verifyPhone(@Valid @RequestBody CodeVerifyRequest req) {
    User user = verificationService.verifyPhoneOtp(req.email(), req.code());
    var tokens = authService.issueTokensForUser(user.getId());
    return toFrontendUser(user, tokens.storeId(), tokens.role(), tokens.accessToken());
  }

  /** Send the phone OTP (rate-limited, enumeration-safe). */
  @PostMapping("/verify-phone/send")
  public java.util.Map<String, Object> sendPhoneOtp(@Valid @RequestBody EmailOnlyRequest req) {
    // Enumeration-safe: same response whether or not the user exists.
    users.findByEmail(req.email().toLowerCase().trim())
        .ifPresent(verificationService::sendPhoneOtp);
    return java.util.Map.of("sent", true);
  }

  public record ResetPasswordRequest(
      @jakarta.validation.constraints.Email String email, String code, String newPassword) {}

  /**
   * Forgot password: send a 6-digit reset code. Same response whether or not
   * the email exists (enumeration protection).
   */
  @PostMapping("/forgot-password")
  public java.util.Map<String, Object> forgotPassword(@Valid @RequestBody EmailOnlyRequest req) {
    passwordResetService.forgotPassword(req.email());
    return java.util.Map.of("sent", true);
  }

  /** Reset password with the emailed 6-digit code. */
  @PostMapping("/reset-password")
  public java.util.Map<String, Object> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    passwordResetService.resetPassword(req.email(), req.code(), req.newPassword());
    return java.util.Map.of("reset", true);
  }

  private FrontendUser toFrontendUser(User user, Long storeId, String role, String token) {
    return new FrontendUser(
        user.getId().toString(),
        user.getFullName(),
        user.getEmail(),
        "merchant",
        role != null ? role.toLowerCase() : "owner",
        storeId != null ? storeId.toString() : null,
        token,
        user.isEmailVerified(),
        user.getPhone(),
        user.isPhoneVerified());
  }
}
