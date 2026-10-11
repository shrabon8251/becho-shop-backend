package com.becho.saas.modules.auth.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Password reset codes. Only the SHA-256 hash of the 6-digit code is
 * stored — a database read never reveals a usable code. Codes expire after
 * 15 minutes, are single-use, and are invalidated after 5 wrong attempts.
 */
@Entity
@Table(name = "password_reset_codes",
    indexes = {
        @Index(name = "idx_prc_user", columnList = "user_id,used_at,created_at"),
    })
public class PasswordResetCode extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  /** Email the code was sent to (for rate-limiting even when no user exists). */
  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(nullable = false)
  private int attempts = 0;

  @Column(name = "used_at")
  private LocalDateTime usedAt;

  public User getUser() { return user; }
  public void setUser(User user) { this.user = user; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getCodeHash() { return codeHash; }
  public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public LocalDateTime getUsedAt() { return usedAt; }
  public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
}
