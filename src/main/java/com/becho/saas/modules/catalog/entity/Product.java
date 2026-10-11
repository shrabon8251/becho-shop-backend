package com.becho.saas.modules.catalog.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products",
    uniqueConstraints = @UniqueConstraint(name = "uq_products_slug", columnNames = {"store_id", "slug"}),
    indexes = @Index(name = "idx_products_browse", columnList = "store_id,status,published_at"))
public class Product extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 255)
  private String slug;

  @Column(columnDefinition = "mediumtext")
  private String description;

  @Column(nullable = false, length = 32)
  private String status = "draft";

  @Column(name = "product_type", length = 128)
  private String productType;

  @Column(length = 128)
  private String vendor;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String tags;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<ProductVariant> variants = new ArrayList<>();

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<ProductImage> images = new ArrayList<>();

  public Store getStore() { return store; }
  public void setStore(Store store) { this.store = store; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public String getDescription() { return description; }
  public void setDescription(String d) { this.description = d; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getProductType() { return productType; }
  public void setProductType(String v) { this.productType = v; }
  public String getVendor() { return vendor; }
  public void setVendor(String v) { this.vendor = v; }
  public String getTags() { return tags; }
  public void setTags(String v) { this.tags = v; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(LocalDateTime v) { this.publishedAt = v; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
  public List<ProductVariant> getVariants() { return variants; }
  public List<ProductImage> getImages() { return images; }
}
