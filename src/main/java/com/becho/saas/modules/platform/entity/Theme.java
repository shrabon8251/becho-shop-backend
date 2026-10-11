package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "themes",
    indexes = @Index(name = "idx_themes_store", columnList = "store_id,status"))
public class Theme extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  /** preset key: minimal | fashion | bold | craft ... */
  @Column(name = "preset_key", length = 64)
  private String presetKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String settings;

  @Column(name = "is_published", nullable = false)
  private boolean published;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getPresetKey() { return presetKey; }
  public void setPresetKey(String v) { this.presetKey = v; }
  public String getSettings() { return settings; }
  public void setSettings(String v) { this.settings = v; }
  public boolean isPublished() { return published; }
  public void setPublished(boolean v) { this.published = v; }
}
