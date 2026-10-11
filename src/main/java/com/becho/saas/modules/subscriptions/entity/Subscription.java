package com.becho.saas.modules.subscriptions.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.customers.entity.Customer;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_subscriptions",
    indexes = {
        @Index(name = "idx_csubs_customer", columnList = "store_id,customer_id,status"),
        @Index(name = "idx_csubs_renewal", columnList = "status,next_billing_at")})
public class Subscription extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Column(name = "plan_name", nullable = false, length = 128)
  private String planName;

  /** daily | weekly | monthly | yearly */
  @Column(name = "`interval`", nullable = false, length = 16)
  private String interval = "monthly";

  @Column(name = "price_minor", nullable = false)
  private long priceMinor;

  @Column(nullable = false, length = 3)
  private String currency = "BDT";

  @Column(nullable = false, length = 32)
  private String status = "active";

  @Column(name = "next_billing_at")
  private LocalDateTime nextBillingAt;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public Customer getCustomer() { return customer; }
  public void setCustomer(Customer c) { this.customer = c; }
  public String getPlanName() { return planName; }
  public void setPlanName(String v) { this.planName = v; }
  public String getInterval() { return interval; }
  public void setInterval(String v) { this.interval = v; }
  public long getPriceMinor() { return priceMinor; }
  public void setPriceMinor(long v) { this.priceMinor = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { this.currency = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public LocalDateTime getNextBillingAt() { return nextBillingAt; }
  public void setNextBillingAt(LocalDateTime v) { this.nextBillingAt = v; }
  public LocalDateTime getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(LocalDateTime v) { this.cancelledAt = v; }
}
