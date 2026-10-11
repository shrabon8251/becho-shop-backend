package com.becho.saas.modules.wallet.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gift_cards",
    uniqueConstraints = @UniqueConstraint(name = "uq_giftcards_code", columnNames = {"code_hash"}),
    indexes = @Index(name = "idx_giftcards_store", columnList = "store_id,status"))
public class GiftCard extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  /**
   * SHA-256 of the code. The raw code is shown ONCE at issuance and never
   * persisted — redemption hashes the presented code and looks up by hash.
   */
  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  /** First chars of the code, for support lookup without the full secret. */
  @Column(name = "code_prefix", nullable = false, length = 8)
  private String codePrefix;

  @Column(name = "initial_minor", nullable = false)
  private long initialMinor;

  @Column(name = "balance_minor", nullable = false)
  private long balanceMinor;

  @Column(nullable = false, length = 3)
  private String currency = "BDT";

  @Column(nullable = false, length = 32)
  private String status = "active";

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "issued_to_email", length = 255)
  private String issuedToEmail;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getCodeHash() { return codeHash; }
  public void setCodeHash(String v) { this.codeHash = v; }
  public String getCodePrefix() { return codePrefix; }
  public void setCodePrefix(String v) { this.codePrefix = v; }
  public long getInitialMinor() { return initialMinor; }
  public void setInitialMinor(long v) { this.initialMinor = v; }
  public long getBalanceMinor() { return balanceMinor; }
  public void setBalanceMinor(long v) { this.balanceMinor = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { this.currency = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
  public String getIssuedToEmail() { return issuedToEmail; }
  public void setIssuedToEmail(String v) { this.issuedToEmail = v; }
}
