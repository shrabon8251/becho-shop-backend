package com.becho.saas.modules.inventory.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements",
    indexes = {
        @Index(name = "idx_movements_lookup", columnList = "store_id,variant_id,created_at"),
        @Index(name = "idx_movements_location", columnList = "location_id,created_at")})
public class StockMovement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(name = "variant_id", nullable = false)
  private Long variantId;

  @Column(name = "location_id", nullable = false)
  private Long locationId;

  @Column(nullable = false)
  private int delta;

  @Column(nullable = false, length = 64)
  private String reason;

  @Column(name = "ref_type", length = 32)
  private String refType;

  @Column(name = "ref_id")
  private Long refId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public Long getStoreId() { return storeId; }
  public void setStoreId(Long v) { this.storeId = v; }
  public Long getVariantId() { return variantId; }
  public void setVariantId(Long v) { this.variantId = v; }
  public Long getLocationId() { return locationId; }
  public void setLocationId(Long v) { this.locationId = v; }
  public int getDelta() { return delta; }
  public void setDelta(int v) { this.delta = v; }
  public String getReason() { return reason; }
  public void setReason(String v) { this.reason = v; }
  public String getRefType() { return refType; }
  public void setRefType(String v) { this.refType = v; }
  public Long getRefId() { return refId; }
  public void setRefId(Long v) { this.refId = v; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
