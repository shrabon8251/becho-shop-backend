package com.becho.saas.modules.platform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log",
    indexes = {
        @Index(name = "idx_alog_store", columnList = "store_id,created_at"),
        @Index(name = "idx_alog_actor", columnList = "actor_id,created_at")})
public class ActivityLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id")
  private Long storeId;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(name = "actor_type", length = 32)
  private String actorType;

  @Column(nullable = false, length = 128)
  private String action;

  @Column(name = "entity_type", length = 64)
  private String entityType;

  @Column(name = "entity_id")
  private Long entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String changes;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public Long getStoreId() { return storeId; }
  public void setStoreId(Long v) { this.storeId = v; }
  public Long getActorId() { return actorId; }
  public void setActorId(Long v) { this.actorId = v; }
  public String getActorType() { return actorType; }
  public void setActorType(String v) { this.actorType = v; }
  public String getAction() { return action; }
  public void setAction(String v) { this.action = v; }
  public String getEntityType() { return entityType; }
  public void setEntityType(String v) { this.entityType = v; }
  public Long getEntityId() { return entityId; }
  public void setEntityId(Long v) { this.entityId = v; }
  public String getChanges() { return changes; }
  public void setChanges(String v) { this.changes = v; }
  public String getIpAddress() { return ipAddress; }
  public void setIpAddress(String v) { this.ipAddress = v; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
