package com.becho.saas.modules.subscriptions.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.catalog.entity.ProductVariant;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;

@Entity
@Table(name = "subscription_plans", indexes = @Index(name = "idx_subplans_store", columnList = "store_id,status"))
public class SubscriptionPlan extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variant_id")
  private ProductVariant variant;

  /** weekly|monthly|quarterly|yearly */
  @Column(name = "`interval`", nullable = false, length = 32)
  private String interval = "monthly";

  @Column(name = "interval_count", nullable = false)
  private int intervalCount = 1;

  @Column(name = "price_minor", nullable = false)
  private long priceMinor;

  @Column(nullable = false, length = 32)
  private String status = "active";

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public ProductVariant getVariant() { return variant; }
  public void setVariant(ProductVariant v) { this.variant = v; }
  public String getInterval() { return interval; }
  public void setInterval(String v) { this.interval = v; }
  public int getIntervalCount() { return intervalCount; }
  public void setIntervalCount(int v) { this.intervalCount = v; }
  public long getPriceMinor() { return priceMinor; }
  public void setPriceMinor(long v) { this.priceMinor = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
}
