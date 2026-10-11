package com.becho.saas.modules.tenancy.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "full_name", nullable = false, length = 128)
  private String fullName;

  @Column(length = 32)
  private String phone;

  @Column(name = "email_verified_at")
  private LocalDateTime emailVerifiedAt;

  @Column(name = "phone_verified_at")
  private LocalDateTime phoneVerifiedAt;

  @Column(name = "terms_accepted_at")
  private LocalDateTime termsAcceptedAt;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts = 0;

  @Column(name = "locked_until")
  private LocalDateTime lockedUntil;

  @Column(nullable = false, length = 32)
  private String status = "active";

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  @Column(name = "platform_admin", nullable = false)
  private boolean platformAdmin = false;

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public LocalDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
  public void setEmailVerifiedAt(LocalDateTime t) { this.emailVerifiedAt = t; }
  public boolean isEmailVerified() { return emailVerifiedAt != null; }
  public LocalDateTime getPhoneVerifiedAt() { return phoneVerifiedAt; }
  public void setPhoneVerifiedAt(LocalDateTime t) { this.phoneVerifiedAt = t; }
  public boolean isPhoneVerified() { return phoneVerifiedAt != null; }
  public LocalDateTime getTermsAcceptedAt() { return termsAcceptedAt; }
  public void setTermsAcceptedAt(LocalDateTime t) { this.termsAcceptedAt = t; }
  public int getFailedLoginAttempts() { return failedLoginAttempts; }
  public void setFailedLoginAttempts(int n) { this.failedLoginAttempts = n; }
  public LocalDateTime getLockedUntil() { return lockedUntil; }
  public void setLockedUntil(LocalDateTime t) { this.lockedUntil = t; }
  public boolean isLockedOut() { return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()); }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDateTime getLastLoginAt() { return lastLoginAt; }
  public void setLastLoginAt(LocalDateTime t) { this.lastLoginAt = t; }
  public boolean isPlatformAdmin() { return platformAdmin; }
  public void setPlatformAdmin(boolean b) { this.platformAdmin = b; }
}
