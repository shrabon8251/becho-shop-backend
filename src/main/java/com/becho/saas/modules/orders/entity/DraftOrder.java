package com.becho.saas.modules.orders.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Draft order: a merchant-composed order not yet placed.
 * Lines JSON: [{variantId, qty, ...}] snapshot.
 */
@Entity
@Table(name = "draft_orders",
    indexes = @Index(name = "idx_draft_orders_store", columnList = "store_id,status"))
public class DraftOrder extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(name = "customer_id")
  private Long customerId;

  @Column(length = 255)
  private String email;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "`lines`", columnDefinition = "json", nullable = false)
  private String lines;

  @Column(name = "subtotal_minor", nullable = false)
  private long subtotalMinor;

  @Column(name = "total_minor", nullable = false)
  private long totalMinor;

  @Column(nullable = false, length = 3)
  private String currency = "BDT";

  @Column(nullable = false, length = 32)
  private String status = "draft";

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public Long getCustomerId() { return customerId; }
  public void setCustomerId(Long v) { this.customerId = v; }
  public String getEmail() { return email; }
  public void setEmail(String v) { this.email = v; }
  public String getLines() { return lines; }
  public void setLines(String v) { this.lines = v; }
  public long getSubtotalMinor() { return subtotalMinor; }
  public void setSubtotalMinor(long v) { this.subtotalMinor = v; }
  public long getTotalMinor() { return totalMinor; }
  public void setTotalMinor(long v) { this.totalMinor = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { this.currency = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
}
