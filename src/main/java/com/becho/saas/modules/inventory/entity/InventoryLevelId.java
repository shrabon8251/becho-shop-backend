package com.becho.saas.modules.inventory.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** Composite PK for inventory_levels. */
public class InventoryLevelId implements Serializable {
  private Long location;
  private Long variant;

  public InventoryLevelId() {}
  public InventoryLevelId(Long location, Long variant) {
    this.location = location;
    this.variant = variant;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof InventoryLevelId other)) return false;
    return Objects.equals(location, other.location) && Objects.equals(variant, other.variant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(location, variant);
  }
}
