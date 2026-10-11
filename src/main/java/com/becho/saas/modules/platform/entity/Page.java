package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "pages",
    uniqueConstraints = @UniqueConstraint(name = "uq_pages_slug", columnNames = {"store_id", "slug"}),
    indexes = @Index(name = "idx_pages_store", columnList = "store_id,status"))
public class Page extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 255)
  private String slug;

  @Column(columnDefinition = "mediumtext")
  private String body;

  /** JSON sections for theme-builder pages. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String sections;

  @Column(nullable = false, length = 32)
  private String status = "draft";

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getTitle() { return title; }
  public void setTitle(String v) { this.title = v; }
  public String getSlug() { return slug; }
  public void setSlug(String v) { this.slug = v; }
  public String getBody() { return body; }
  public void setBody(String v) { this.body = v; }
  public String getSections() { return sections; }
  public void setSections(String v) { this.sections = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
}
