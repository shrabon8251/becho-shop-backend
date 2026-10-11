package com.becho.saas.modules.auth.web;

import com.becho.saas.common.ApiResponse;
import com.becho.saas.modules.auth.service.AuthService;
import com.becho.saas.modules.auth.service.PasswordResetService;
import com.becho.saas.modules.auth.web.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final PasswordResetService passwordResetService;

  public AuthController(AuthService authService, PasswordResetService passwordResetService) {
    this.authService = authService;
    this.passwordResetService = passwordResetService;
  }

  @PostMapping("/register")
  public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
    return ApiResponse.ok(authService.register(req));
  }

  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
    return ApiResponse.ok(authService.login(req));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    return ApiResponse.ok(authService.refresh(req));
  }

  @PostMapping("/logout")
  public ApiResponse<String> logout(@Valid @RequestBody LogoutRequest req) {
    authService.logout(req);
    return ApiResponse.ok("logged_out");
  }

  @PostMapping("/forgot-password")
  public ApiResponse<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
    passwordResetService.forgotPassword(req.email());
    // Same response whether or not the email exists (enumeration protection).
    return ApiResponse.ok("reset_code_sent");
  }

  @PostMapping("/reset-password")
  public ApiResponse<String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    passwordResetService.resetPassword(req.email(), req.code(), req.newPassword());
    return ApiResponse.ok("password_reset");
  }
}
