package com.becho.saas.modules.catalog.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Product bundle/kit: a named set of items sold at a bundle price.
 * Items JSON: [{productId, variantId?, qty}].
 */
@Entity
@Table(name = "bundles",
    indexes = @Index(name = "idx_bundles_store", columnList = "store_id,active"))
public class Bundle extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json", nullable = false)
  private String items;

  @Column(name = "price_minor", nullable = false)
  private long priceMinor;

  @Column(length = 1024)
  private String image;

  @Column(nullable = false)
  private boolean active = true;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getItems() { return items; }
  public void setItems(String v) { this.items = v; }
  public long getPriceMinor() { return priceMinor; }
  public void setPriceMinor(long v) { this.priceMinor = v; }
  public String getImage() { return image; }
  public void setImage(String v) { this.image = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { this.active = v; }
}
