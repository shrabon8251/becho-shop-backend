package com.becho.saas.modules.marketing.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.customers.entity.Customer;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "abandoned_checkouts", indexes = @Index(name = "idx_abandoned_store", columnList = "store_id,recovered_at,created_at"))
public class AbandonedCheckout extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id")
  private Customer customer;

  @Column(length = 255)
  private String email;

  @Column(nullable = false, columnDefinition = "json")
  private String cart;

  @Column(name = "subtotal_minor", nullable = false)
  private long subtotalMinor;

  @Column(name = "total_minor", nullable = false)
  private long totalMinor;

  @Column(nullable = false, length = 3)
  private String currency = "BDT";

  @Column(name = "recovered_at")
  private LocalDateTime recoveredAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public Customer getCustomer() { return customer; }
  public void setCustomer(Customer c) { this.customer = c; }
  public String getEmail() { return email; }
  public void setEmail(String v) { this.email = v; }
  public String getCart() { return cart; }
  public void setCart(String v) { this.cart = v; }
  public long getSubtotalMinor() { return subtotalMinor; }
  public void setSubtotalMinor(long v) { this.subtotalMinor = v; }
  public long getTotalMinor() { return totalMinor; }
  public void setTotalMinor(long v) { this.totalMinor = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { this.currency = v; }
  public LocalDateTime getRecoveredAt() { return recoveredAt; }
  public void setRecoveredAt(LocalDateTime v) { this.recoveredAt = v; }
}
