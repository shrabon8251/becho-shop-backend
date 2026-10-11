package com.becho.saas.modules.auth.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persisted refresh token for rotation + revocation.
 * Only the SHA-256 hash is stored; reuse of a rotated token revokes the family (theft detection).
 */
@Entity
@Table(name = "refresh_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uq_refresh_token_hash", columnNames = "token_hash"),
    indexes = @Index(name = "idx_refresh_user", columnList = "user_id,revoked_at"))
public class RefreshToken extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Column(name = "replaced_by")
  private Long replacedBy;

  @Column(length = 45)
  private String ip;

  public User getUser() { return user; }
  public void setUser(User u) { this.user = u; }
  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String v) { this.tokenHash = v; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
  public LocalDateTime getRevokedAt() { return revokedAt; }
  public void setRevokedAt(LocalDateTime v) { this.revokedAt = v; }
  public Long getReplacedBy() { return replacedBy; }
  public void setReplacedBy(Long v) { this.replacedBy = v; }
  public String getIp() { return ip; }
  public void setIp(String v) { this.ip = v; }
}
