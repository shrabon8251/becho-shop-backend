package com.becho.saas.modules.customers.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers",
    uniqueConstraints = @UniqueConstraint(name = "uq_customers_email", columnNames = {"store_id", "email"}),
    indexes = {
        @Index(name = "idx_customers_phone", columnList = "store_id,phone"),
        @Index(name = "idx_customers_name", columnList = "store_id,last_name,first_name")})
public class Customer extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(length = 255)
  private String email;

  @Column(length = 32)
  private String phone;

  @Column(name = "first_name", length = 64)
  private String firstName;

  @Column(name = "last_name", length = 64)
  private String lastName;

  /** Argon2id hash; NULL for guest customers. */
  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "email_verified_at")
  private LocalDateTime emailVerifiedAt;

  @Column(name = "phone_verified_at")
  private LocalDateTime phoneVerifiedAt;

  @Column(name = "accepts_marketing", nullable = false)
  private boolean acceptsMarketing;

  @Column(name = "total_spent_minor", nullable = false)
  private long totalSpentMinor;

  @Column(name = "orders_count", nullable = false)
  private int ordersCount;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String tags;

  @Column(columnDefinition = "text")
  private String note;

  @Column(name = "credit_minor", nullable = false)
  private long creditMinor = 0;

  @Column(nullable = false, length = 32)
  private String status = "active";

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts = 0;

  @Column(name = "locked_until")
  private LocalDateTime lockedUntil;

  public int getFailedLoginAttempts() { return failedLoginAttempts; }
  public void setFailedLoginAttempts(int v) { this.failedLoginAttempts = v; }
  public LocalDateTime getLockedUntil() { return lockedUntil; }
  public void setLockedUntil(LocalDateTime v) { this.lockedUntil = v; }
  public boolean isLockedOut() {
    return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
  }

  @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<CustomerAddress> addresses = new ArrayList<>();

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getEmail() { return email; }
  public void setEmail(String v) { this.email = v; }
  public String getPhone() { return phone; }
  public void setPhone(String v) { this.phone = v; }
  public String getFirstName() { return firstName; }
  public void setFirstName(String v) { this.firstName = v; }
  public String getLastName() { return lastName; }
  public void setLastName(String v) { this.lastName = v; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String v) { this.passwordHash = v; }
  public LocalDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
  public void setEmailVerifiedAt(LocalDateTime v) { this.emailVerifiedAt = v; }
  public boolean isEmailVerified() { return emailVerifiedAt != null; }
  public LocalDateTime getPhoneVerifiedAt() { return phoneVerifiedAt; }
  public void setPhoneVerifiedAt(LocalDateTime v) { this.phoneVerifiedAt = v; }
  public boolean isPhoneVerified() { return phoneVerifiedAt != null; }
  public boolean isAcceptsMarketing() { return acceptsMarketing; }
  public void setAcceptsMarketing(boolean v) { this.acceptsMarketing = v; }
  public long getTotalSpentMinor() { return totalSpentMinor; }
  public void setTotalSpentMinor(long v) { this.totalSpentMinor = v; }
  public int getOrdersCount() { return ordersCount; }
  public void setOrdersCount(int v) { this.ordersCount = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
  public List<CustomerAddress> getAddresses() { return addresses; }
  public String getNote() { return note; }
  public void setNote(String v) { this.note = v; }
  public String getTags() { return tags; }
  public void setTags(String v) { this.tags = v; }
  public long getCreditMinor() { return creditMinor; }
  public void setCreditMinor(long v) { this.creditMinor = v; }
}
