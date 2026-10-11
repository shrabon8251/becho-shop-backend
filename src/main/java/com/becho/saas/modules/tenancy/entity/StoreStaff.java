package com.becho.saas.modules.tenancy.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "store_staff",
    uniqueConstraints = @UniqueConstraint(name = "uq_staff_store_user", columnNames = {"store_id", "user_id"}))
public class StoreStaff extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** owner | admin | staff | support */
  @Column(nullable = false, length = 32)
  private String role = "staff";

  /** Fine-grained overrides, enforced server-side. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String permissions;

  @Column(nullable = false, length = 32)
  private String status = "active";

  public Store getStore() { return store; }
  public void setStore(Store store) { this.store = store; }
  public User getUser() { return user; }
  public void setUser(User user) { this.user = user; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getPermissions() { return permissions; }
  public void setPermissions(String permissions) { this.permissions = permissions; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
