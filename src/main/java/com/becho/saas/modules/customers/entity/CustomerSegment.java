package com.becho.saas.modules.customers.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Customer segment: a named saved filter over customers.
 * Rules JSON: [{field, op, value}, ...].
 */
@Entity
@Table(name = "customer_segments",
    indexes = @Index(name = "idx_segments_store", columnList = "store_id"))
public class CustomerSegment extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String rules;

  @Column(name = "customer_count", nullable = false)
  private int customerCount;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getRules() { return rules; }
  public void setRules(String v) { this.rules = v; }
  public int getCustomerCount() { return customerCount; }
  public void setCustomerCount(int v) { this.customerCount = v; }
}
