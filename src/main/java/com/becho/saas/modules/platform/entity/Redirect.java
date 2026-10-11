package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;

@Entity
@Table(name = "redirects",
    uniqueConstraints = @UniqueConstraint(name = "uq_redirects_from", columnNames = {"store_id", "from_path"}),
    indexes = @Index(name = "idx_redirects_store", columnList = "store_id"))
public class Redirect extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(name = "from_path", nullable = false, length = 1024)
  private String fromPath;

  @Column(name = "to_path", nullable = false, length = 1024)
  private String toPath;

  @Column(name = "status_code", nullable = false)
  private int statusCode = 301;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getFromPath() { return fromPath; }
  public void setFromPath(String v) { this.fromPath = v; }
  public String getToPath() { return toPath; }
  public void setToPath(String v) { this.toPath = v; }
  public int getStatusCode() { return statusCode; }
  public void setStatusCode(int v) { this.statusCode = v; }
}
