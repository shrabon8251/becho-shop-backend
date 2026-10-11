package com.becho.saas.modules.shipping.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Delivery/shipping profile: a named set of zones and product scoping.
 * Zones JSON: [{id, name, rates:[...]}, ...].
 */
@Entity
@Table(name = "shipping_profiles",
    indexes = @Index(name = "idx_ship_profiles_store", columnList = "store_id,active"))
public class ShippingProfile extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String zones;

  @Column(nullable = false)
  private boolean active = true;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getZones() { return zones; }
  public void setZones(String v) { this.zones = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { this.active = v; }
}
