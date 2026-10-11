package com.becho.saas.modules.catalog.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_variants",
    uniqueConstraints = @UniqueConstraint(name = "uq_variants_sku", columnNames = {"store_id", "sku"}),
    indexes = @Index(name = "idx_variants_product", columnList = "product_id,position"))
public class ProductVariant extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(length = 64)
  private String sku;

  @Column(length = 64)
  private String barcode;

  @Column(length = 128)
  private String option1;
  @Column(length = 128)
  private String option2;
  @Column(length = 128)
  private String option3;

  @Column(name = "price_minor", nullable = false)
  private long priceMinor;

  @Column(name = "compare_at_minor")
  private Long compareAtMinor;

  @Column(name = "cost_minor")
  private Long costMinor;

  @Column(name = "weight_grams")
  private Integer weightGrams;

  @Column(nullable = false)
  private boolean taxable = true;

  @Column(name = "inventory_policy", nullable = false, length = 32)
  private String inventoryPolicy = "deny";

  @Column(nullable = false)
  private int position;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public Product getProduct() { return product; }
  public void setProduct(Product p) { this.product = p; }
  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getSku() { return sku; }
  public void setSku(String v) { this.sku = v; }
  public String getBarcode() { return barcode; }
  public void setBarcode(String v) { this.barcode = v; }
  public String getOption1() { return option1; }
  public void setOption1(String v) { this.option1 = v; }
  public String getOption2() { return option2; }
  public void setOption2(String v) { this.option2 = v; }
  public String getOption3() { return option3; }
  public void setOption3(String v) { this.option3 = v; }
  public long getPriceMinor() { return priceMinor; }
  public void setPriceMinor(long v) { this.priceMinor = v; }
  public Long getCompareAtMinor() { return compareAtMinor; }
  public void setCompareAtMinor(Long v) { this.compareAtMinor = v; }
  public Long getCostMinor() { return costMinor; }
  public void setCostMinor(Long v) { this.costMinor = v; }
  public Integer getWeightGrams() { return weightGrams; }
  public void setWeightGrams(Integer v) { this.weightGrams = v; }
  public boolean isTaxable() { return taxable; }
  public void setTaxable(boolean v) { this.taxable = v; }
  public String getInventoryPolicy() { return inventoryPolicy; }
  public void setInventoryPolicy(String v) { this.inventoryPolicy = v; }
  public int getPosition() { return position; }
  public void setPosition(int v) { this.position = v; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
}
