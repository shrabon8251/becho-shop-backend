package com.becho.saas.modules.inventory.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "locations",
    indexes = @Index(name = "idx_locations_store", columnList = "store_id,status"))
public class Location extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String address;

  @Column(name = "is_default", nullable = false)
  private boolean defaultLocation;

  @Column(nullable = false, length = 32)
  private String status = "active";

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getAddress() { return address; }
  public void setAddress(String v) { this.address = v; }
  public boolean isDefaultLocation() { return defaultLocation; }
  public void setDefaultLocation(boolean v) { this.defaultLocation = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
}
