package com.becho.saas.modules.wallet.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payouts", indexes = @Index(name = "idx_payouts_store", columnList = "store_id,status,created_at"))
public class Payout extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency = "BDT";

  /** pending|in_transit|paid|failed */
  @Column(nullable = false, length = 32)
  private String status = "pending";

  @Column(length = 64)
  private String gateway;

  @Column(name = "gateway_ref", length = 128)
  private String gatewayRef;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public long getAmountMinor() { return amountMinor; }
  public void setAmountMinor(long v) { this.amountMinor = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { this.currency = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getGateway() { return gateway; }
  public void setGateway(String v) { this.gateway = v; }
  public String getGatewayRef() { return gatewayRef; }
  public void setGatewayRef(String v) { this.gatewayRef = v; }
  public LocalDate getPeriodStart() { return periodStart; }
  public void setPeriodStart(LocalDate v) { this.periodStart = v; }
  public LocalDate getPeriodEnd() { return periodEnd; }
  public void setPeriodEnd(LocalDate v) { this.periodEnd = v; }
  public LocalDateTime getPaidAt() { return paidAt; }
  public void setPaidAt(LocalDateTime v) { this.paidAt = v; }
}
