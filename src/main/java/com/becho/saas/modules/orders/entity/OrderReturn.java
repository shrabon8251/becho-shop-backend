package com.becho.saas.modules.orders.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Customer return request against an order.
 * Status: requested | approved | rejected | completed.
 */
@Entity
@Table(name = "returns",
    indexes = {
      @Index(name = "idx_returns_store", columnList = "store_id,status"),
      @Index(name = "idx_returns_order", columnList = "order_id")
    })
public class OrderReturn extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(nullable = false, length = 255)
  private String reason;

  @Column(nullable = false, length = 32)
  private String status = "requested";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String items;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public Order getOrder() { return order; }
  public void setOrder(Order o) { this.order = o; }
  public String getReason() { return reason; }
  public void setReason(String v) { this.reason = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getItems() { return items; }
  public void setItems(String v) { this.items = v; }
}
