package com.becho.saas.modules.orders.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.catalog.entity.Product;
import com.becho.saas.modules.catalog.entity.ProductVariant;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;

@Entity
@Table(name = "order_lines",
    indexes = {
        @Index(name = "idx_olines_order", columnList = "order_id"),
        @Index(name = "idx_olines_variant", columnList = "store_id,variant_id")})
public class OrderLine extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variant_id")
  private ProductVariant variant;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(length = 64)
  private String sku;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "price_minor", nullable = false)
  private long priceMinor;

  @Column(name = "discount_minor", nullable = false)
  private long discountMinor;

  @Column(name = "tax_minor", nullable = false)
  private long taxMinor;

  @Column(name = "total_minor", nullable = false)
  private long totalMinor;

  public Order getOrder() { return order; }
  public void setOrder(Order o) { this.order = o; }
  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public Product getProduct() { return product; }
  public void setProduct(Product p) { this.product = p; }
  public ProductVariant getVariant() { return variant; }
  public void setVariant(ProductVariant v) { this.variant = v; }
  public String getTitle() { return title; }
  public void setTitle(String v) { this.title = v; }
  public String getSku() { return sku; }
  public void setSku(String v) { this.sku = v; }
  public int getQuantity() { return quantity; }
  public void setQuantity(int v) { this.quantity = v; }
  public long getPriceMinor() { return priceMinor; }
  public void setPriceMinor(long v) { this.priceMinor = v; }
  public long getDiscountMinor() { return discountMinor; }
  public void setDiscountMinor(long v) { this.discountMinor = v; }
  public long getTaxMinor() { return taxMinor; }
  public void setTaxMinor(long v) { this.taxMinor = v; }
  public long getTotalMinor() { return totalMinor; }
  public void setTotalMinor(long v) { this.totalMinor = v; }
}
