package com.becho.saas.modules.inventory.entity;

import com.becho.saas.modules.catalog.entity.ProductVariant;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_levels")
@IdClass(InventoryLevelId.class)
public class InventoryLevel {

  @Id
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "location_id", nullable = false)
  private Location location;

  @Id
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "variant_id", nullable = false)
  private ProductVariant variant;

  @Column(name = "on_hand", nullable = false)
  private int onHand;

  @Column(nullable = false)
  private int reserved;

  @Column(name = "reorder_point", nullable = false)
  private int reorderPoint;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt = LocalDateTime.now();

  public Location getLocation() { return location; }
  public void setLocation(Location v) { this.location = v; }
  public ProductVariant getVariant() { return variant; }
  public void setVariant(ProductVariant v) { this.variant = v; }
  public int getOnHand() { return onHand; }
  public void setOnHand(int v) { this.onHand = v; }
  public int getReserved() { return reserved; }
  public void setReserved(int v) { this.reserved = v; }
  public int getAvailable() { return onHand - reserved; }
  public int getReorderPoint() { return reorderPoint; }
  public void setReorderPoint(int v) { this.reorderPoint = v; }
}
