package com.becho.saas.modules.billing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Platform promo code (e.g. BECHO10). Redemption is atomic: the row is
 * locked with SELECT ... FOR UPDATE inside a transaction, usage is checked
 * and incremented, and promo_redemptions has a UNIQUE(code, store_id)
 * constraint so a store can never redeem the same code twice — even under
 * concurrent requests.
 */
@Entity
@Table(name = "promo_codes")
public class PromoCode {

  @Id
  @Column(length = 32)
  private String code;

  @Column(name = "percent_off", nullable = false, precision = 5, scale = 2)
  private BigDecimal percentOff = BigDecimal.ZERO;

  @Column(name = "max_uses", nullable = false)
  private int maxUses = 1_000_000;

  @Column(name = "used_count", nullable = false)
  private int usedCount = 0;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public BigDecimal getPercentOff() { return percentOff; }
  public void setPercentOff(BigDecimal percentOff) { this.percentOff = percentOff; }
  public int getMaxUses() { return maxUses; }
  public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
  public int getUsedCount() { return usedCount; }
  public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

  public boolean isUsable() {
    return active
        && usedCount < maxUses
        && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
  }
}
