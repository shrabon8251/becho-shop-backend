package com.becho.saas.modules.catalog.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "collections",
    uniqueConstraints = @UniqueConstraint(name = "uq_collections_slug", columnNames = {"store_id", "slug"}),
    indexes = @Index(name = "idx_collections_store", columnList = "store_id,status"))
public class Collection extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 255)
  private String slug;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "image_url", length = 1024)
  private String imageUrl;

  @Column(name = "sort_order", nullable = false, length = 32)
  private String sortOrder = "manual";

  @Column(nullable = false, length = 32)
  private String status = "draft";

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getTitle() { return title; }
  public void setTitle(String v) { this.title = v; }
  public String getSlug() { return slug; }
  public void setSlug(String v) { this.slug = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { this.description = v; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String v) { this.imageUrl = v; }
  public String getSortOrder() { return sortOrder; }
  public void setSortOrder(String v) { this.sortOrder = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(LocalDateTime v) { this.publishedAt = v; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
}
