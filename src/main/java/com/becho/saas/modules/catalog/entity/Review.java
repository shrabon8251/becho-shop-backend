package com.becho.saas.modules.catalog.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;

/**
 * Product review left by a customer.
 */
@Entity
@Table(name = "reviews",
    indexes = @Index(name = "idx_reviews_store_product",
        columnList = "store_id,product_id,created_at"))
public class Review extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false)
  private int stars;

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String text;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public Product getProduct() { return product; }
  public void setProduct(Product p) { this.product = p; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public int getStars() { return stars; }
  public void setStars(int v) { this.stars = v; }
  public String getText() { return text; }
  public void setText(String v) { this.text = v; }
}
