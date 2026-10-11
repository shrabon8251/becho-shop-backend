package com.becho.saas.modules.platform.entity;

import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Per-store apps configuration (announcement bar, reviews app, installed
 * app ids). Stored as raw JSON to match the frontend's AppsConfig shape:
 * { bar: { on, text }, reviews: { on }, installed?: string[] }.
 */
@Entity
@Table(name = "store_apps_config")
public class StoreAppsConfig {

  @Id
  @Column(name = "store_id")
  private Long storeId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "store_id")
  private Store store;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String config;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = LocalDateTime.now();
  }

  public Long getStoreId() { return storeId; }
  public Store getStore() { return store; }
  public void setStore(Store store) { this.store = store; }
  public String getConfig() { return config; }
  public void setConfig(String config) { this.config = config; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
