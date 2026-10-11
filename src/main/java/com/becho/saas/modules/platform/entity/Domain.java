package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;

/**
 * Custom domain attached to a store. One domain per row; the store's
 * `domain` column is legacy single-domain storage.
 */
@Entity
@Table(name = "domains",
    uniqueConstraints = @UniqueConstraint(name = "uq_domains_domain", columnNames = "domain"),
    indexes = @Index(name = "idx_domains_store", columnList = "store_id"))
public class Domain extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 255)
  private String domain;

  @Column(name = "`primary`", nullable = false)
  private boolean primary;

  @Column(nullable = false, length = 32)
  private String status = "Pending";

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getDomain() { return domain; }
  public void setDomain(String v) { this.domain = v; }
  public boolean isPrimary() { return primary; }
  public void setPrimary(boolean v) { this.primary = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
}
