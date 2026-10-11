package com.becho.saas.modules.discounts.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "discounts",
    uniqueConstraints = @UniqueConstraint(name = "uq_discounts_code", columnNames = {"store_id", "code"}),
    indexes = @Index(name = "idx_discounts_store", columnList = "store_id,status"))
public class Discount extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 64)
  private String title;

  @Column(nullable = false, length = 64)
  private String code;

  /** percentage | fixed_amount | free_shipping | buy_x_get_y */
  @Column(name = "discount_type", nullable = false, length = 32)
  private String discountType;

  @Column(name = "value_minor")
  private Long valueMinor;

  @Column(name = "value_percent", precision = 5, scale = 2)
  private java.math.BigDecimal valuePercent;

  @Column(name = "minimum_subtotal_minor")
  private Long minimumSubtotalMinor;

  @Column(name = "usage_limit")
  private Integer usageLimit;

  @Column(name = "used_count", nullable = false)
  private int usedCount;

  @Column(name = "per_customer_limit")
  private Integer perCustomerLimit;

  @Column(name = "starts_at")
  private LocalDateTime startsAt;

  @Column(name = "ends_at")
  private LocalDateTime endsAt;

  @Column(nullable = false, length = 32)
  private String status = "active";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String conditions;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getTitle() { return title; }
  public void setTitle(String v) { this.title = v; }
  public String getCode() { return code; }
  public void setCode(String v) { this.code = v; }
  public String getDiscountType() { return discountType; }
  public void setDiscountType(String v) { this.discountType = v; }
  public Long getValueMinor() { return valueMinor; }
  public void setValueMinor(Long v) { this.valueMinor = v; }
  public java.math.BigDecimal getValuePercent() { return valuePercent; }
  public void setValuePercent(java.math.BigDecimal v) { this.valuePercent = v; }
  public Long getMinimumSubtotalMinor() { return minimumSubtotalMinor; }
  public void setMinimumSubtotalMinor(Long v) { this.minimumSubtotalMinor = v; }
  public Integer getUsageLimit() { return usageLimit; }
  public void setUsageLimit(Integer v) { this.usageLimit = v; }
  public int getUsedCount() { return usedCount; }
  public void setUsedCount(int v) { this.usedCount = v; }
  public Integer getPerCustomerLimit() { return perCustomerLimit; }
  public void setPerCustomerLimit(Integer v) { this.perCustomerLimit = v; }
  public LocalDateTime getStartsAt() { return startsAt; }
  public void setStartsAt(LocalDateTime v) { this.startsAt = v; }
  public LocalDateTime getEndsAt() { return endsAt; }
  public void setEndsAt(LocalDateTime v) { this.endsAt = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getConditions() { return conditions; }
  public void setConditions(String v) { this.conditions = v; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
}
