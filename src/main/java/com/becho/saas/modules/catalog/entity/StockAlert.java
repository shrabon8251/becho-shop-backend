package com.becho.saas.modules.catalog.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * "Back in Stock" alert subscriptions. A shopper subscribes with an email;
 * when the merchant restocks the product and triggers notify, each pending
 * subscriber gets an email and the row is marked notified.
 *
 * <p>Standalone entity (no BaseEntity): the table has no updated_at column.
 */
@Entity
@Table(name = "stock_alerts",
    indexes = {
        @Index(name = "idx_stock_alerts_pending", columnList = "store_id,product_id,notified"),
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_stock_alerts", columnNames = {"store_id", "product_id", "email"}),
    })
public class StockAlert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(nullable = false)
  private boolean notified = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public Long getStoreId() { return storeId; }
  public void setStoreId(Long v) { this.storeId = v; }
  public Product getProduct() { return product; }
  public void setProduct(Product v) { this.product = v; }
  public String getEmail() { return email; }
  public void setEmail(String v) { this.email = v; }
  public boolean isNotified() { return notified; }
  public void setNotified(boolean v) { this.notified = v; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
