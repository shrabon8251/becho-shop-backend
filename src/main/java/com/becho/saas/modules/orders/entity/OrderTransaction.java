package com.becho.saas.modules.orders.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_transactions",
    uniqueConstraints = @UniqueConstraint(name = "uq_otxn_gateway",
        columnNames = {"store_id", "gateway", "gateway_txn_id"}),
    indexes = @Index(name = "idx_otxn_order", columnList = "order_id,created_at"))
public class OrderTransaction extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  /** sale | authorization | capture | refund | void */
  @Column(nullable = false, length = 32)
  private String kind;

  @Column(nullable = false, length = 64)
  private String gateway;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 32)
  private String status = "pending";

  @Column(name = "gateway_txn_id", length = 128)
  private String gatewayTxnId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String raw;

  public Order getOrder() { return order; }
  public void setOrder(Order o) { this.order = o; }
  public Long getStoreId() { return storeId; }
  public void setStoreId(Long v) { this.storeId = v; }
  public String getKind() { return kind; }
  public void setKind(String v) { this.kind = v; }
  public String getGateway() { return gateway; }
  public void setGateway(String v) { this.gateway = v; }
  public long getAmountMinor() { return amountMinor; }
  public void setAmountMinor(long v) { this.amountMinor = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { this.currency = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getGatewayTxnId() { return gatewayTxnId; }
  public void setGatewayTxnId(String v) { this.gatewayTxnId = v; }
  public String getRaw() { return raw; }
  public void setRaw(String v) { this.raw = v; }
}
