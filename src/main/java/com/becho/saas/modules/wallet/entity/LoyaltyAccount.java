package com.becho.saas.modules.wallet.entity;

import com.becho.saas.modules.customers.entity.Customer;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row per customer; customer_id is the primary key (matches V7 schema).
 */
@Entity
@Table(name = "loyalty_accounts",
    uniqueConstraints = @UniqueConstraint(name = "uq_loyalty_customer",
        columnNames = {"store_id", "customer_id"}))
public class LoyaltyAccount {

  @Id
  @Column(name = "customer_id")
  private Long customerId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Column(nullable = false)
  private long points;

  @Column(name = "lifetime_points", nullable = false)
  private int lifetimePoints;

  @Column(nullable = false, length = 32)
  private String tier = "bronze";

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void touch() { this.updatedAt = LocalDateTime.now(); }

  public Long getCustomerId() { return customerId; }
  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public Customer getCustomer() { return customer; }
  public void setCustomer(Customer c) { this.customer = c; }
  public long getPoints() { return points; }
  public void setPoints(long v) { this.points = v; }
  public int getLifetimePoints() { return lifetimePoints; }
  public void setLifetimePoints(int v) { this.lifetimePoints = v; }
  public String getTier() { return tier; }
  public void setTier(String v) { this.tier = v; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
