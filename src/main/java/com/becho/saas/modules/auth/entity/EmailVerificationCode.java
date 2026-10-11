package com.becho.saas.modules.auth.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Email verification codes. Only the SHA-256 hash of the 6-digit code is
 * stored — a database read never reveals a usable code. Codes expire after
 * 15 minutes and are invalidated after 5 wrong attempts.
 */
@Entity
@Table(name = "email_verification_codes",
    indexes = {
        @Index(name = "idx_evc_user", columnList = "user_id,used_at,created_at"),
    })
public class EmailVerificationCode extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

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
  public String getCodeHash() { return codeHash; }
  public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public LocalDateTime getUsedAt() { return usedAt; }
  public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

  public boolean isUsable() {
    return usedAt == null && attempts < 5 && expiresAt.isAfter(LocalDateTime.now());
  }
}
