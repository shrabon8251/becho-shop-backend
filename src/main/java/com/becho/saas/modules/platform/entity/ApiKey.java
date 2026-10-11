package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_keys",
    indexes = @Index(name = "idx_apikeys_store", columnList = "store_id,status"))
public class ApiKey extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  /** becho_ prefix + last 6 shown; full key shown once. */
  @Column(name = "key_prefix", nullable = false, length = 16)
  private String keyPrefix;

  /** SHA-256 of the raw key. */
  @Column(name = "key_hash", nullable = false, length = 64, unique = true)
  private String keyHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String scopes;

  @Column(nullable = false, length = 32)
  private String status = "active";

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getKeyPrefix() { return keyPrefix; }
  public void setKeyPrefix(String v) { this.keyPrefix = v; }
  public String getKeyHash() { return keyHash; }
  public void setKeyHash(String v) { this.keyHash = v; }
  public String getScopes() { return scopes; }
  public void setScopes(String v) { this.scopes = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
  public LocalDateTime getLastUsedAt() { return lastUsedAt; }
  public void setLastUsedAt(LocalDateTime v) { this.lastUsedAt = v; }
}
