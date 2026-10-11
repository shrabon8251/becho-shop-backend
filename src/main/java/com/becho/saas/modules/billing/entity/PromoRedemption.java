package com.becho.saas.modules.billing.entity;

import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Records that a store redeemed a promo code. UNIQUE(code, store_id) makes
 * double-redemption impossible at the database level.
 */
@Entity
@Table(name = "promo_redemptions",
    uniqueConstraints = @UniqueConstraint(name = "uq_promo_store", columnNames = {"code", "store_id"}),
    indexes = @Index(name = "idx_redemption_store", columnList = "store_id"))
public class PromoRedemption {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "code", nullable = false)
  private PromoCode promoCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(name = "redeemed_at", nullable = false, updatable = false)
  private LocalDateTime redeemedAt;

  @PrePersist
  void prePersist() {
    redeemedAt = LocalDateTime.now();
  }

  public Long getId() { return id; }
  public PromoCode getPromoCode() { return promoCode; }
  public void setPromoCode(PromoCode promoCode) { this.promoCode = promoCode; }
  public Store getStore() { return store; }
  public void setStore(Store store) { this.store = store; }
  public LocalDateTime getRedeemedAt() { return redeemedAt; }
}
