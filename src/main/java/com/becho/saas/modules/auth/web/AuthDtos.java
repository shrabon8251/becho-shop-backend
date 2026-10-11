package com.becho.saas.modules.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

  public record RegisterRequest(
      @NotBlank String fullName,
      @Email @NotBlank String email,
      @Size(min = 8, max = 72) String password,
      @NotBlank String storeName,
      java.time.LocalDateTime termsAcceptedAt) {

    /** Backwards-compatible constructor without terms timestamp. */
    public RegisterRequest(String fullName, String email, String password, String storeName) {
      this(fullName, email, password, storeName, null);
    }
  }

  public record LoginRequest(
      @Email @NotBlank String email,
      @NotBlank String password,
      Long storeId) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
                              Long userId, Long storeId, String role) {}

  public record RegisterResponse(Long userId, String email, boolean emailVerificationRequired,
                                 String message) {}

  public record LogoutRequest(@NotBlank String refreshToken) {}

  public record ForgotPasswordRequest(@Email @NotBlank String email) {}

  public record ResetPasswordRequest(
      @Email @NotBlank String email,
      @NotBlank String code,
      @Size(min = 8, max = 72) String newPassword) {}
}
