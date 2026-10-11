package com.becho.saas.modules.catalog.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "product_images",
    indexes = @Index(name = "idx_images_product", columnList = "product_id,position"))
public class ProductImage extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variant_id")
  private ProductVariant variant;

  @Column(nullable = false, length = 1024)
  private String url;

  @Column(length = 255)
  private String alt;

  @Column(nullable = false)
  private int position;

  public Product getProduct() { return product; }
  public void setProduct(Product p) { this.product = p; }
  public ProductVariant getVariant() { return variant; }
  public void setVariant(ProductVariant v) { this.variant = v; }
  public String getUrl() { return url; }
  public void setUrl(String v) { this.url = v; }
  public String getAlt() { return alt; }
  public void setAlt(String v) { this.alt = v; }
  public int getPosition() { return position; }
  public void setPosition(int v) { this.position = v; }
}
