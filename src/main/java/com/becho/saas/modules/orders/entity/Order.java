package com.becho.saas.modules.orders.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.customers.entity.Customer;
import com.becho.saas.modules.inventory.entity.Location;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders",
    uniqueConstraints = @UniqueConstraint(name = "uq_orders_number", columnNames = {"store_id", "order_number"}),
    indexes = {
        @Index(name = "idx_orders_customer", columnList = "store_id,customer_id,created_at"),
        @Index(name = "idx_orders_financial", columnList = "store_id,financial_status,created_at"),
        @Index(name = "idx_orders_fulfillment", columnList = "store_id,fulfillment_status,created_at"),
        @Index(name = "idx_orders_created", columnList = "store_id,created_at")})
public class Order extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(name = "order_number", nullable = false)
  private long orderNumber;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id")
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id")
  private Location location;

  @Column(length = 255)
  private String email;

  @Column(length = 32)
  private String phone;

  @Column(name = "financial_status", nullable = false, length = 32)
  private String financialStatus = "pending";

  @Column(name = "fulfillment_status", nullable = false, length = 32)
  private String fulfillmentStatus = "unfulfilled";

  @Column(name = "subtotal_minor", nullable = false)
  private long subtotalMinor;

  @Column(name = "discount_minor", nullable = false)
  private long discountMinor;

  @Column(name = "shipping_minor", nullable = false)
  private long shippingMinor;

  @Column(name = "tax_minor", nullable = false)
  private long taxMinor;

  @Column(name = "total_minor", nullable = false)
  private long totalMinor;

  @Column(nullable = false, length = 3)
  private String currency = "BDT";

  @Column(name = "payment_method", length = 64)
  private String paymentMethod;

  @Column(nullable = false, length = 32)
  private String source = "web";

  @Column(columnDefinition = "text")
  private String notes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "billing_address", columnDefinition = "json")
  private String billingAddress;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "shipping_address", columnDefinition = "json")
  private String shippingAddress;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String tags;

  @Column(name = "discount_code", length = 64)
  private String discountCode;

  @Column(name = "gift_code_hash", length = 64)
  private String giftCodeHash;

  @Column(name = "gift_applied_minor")
  private long giftAppliedMinor;

  @Column(name = "loyalty_points_used")
  private int loyaltyPointsUsed;

  @Column(name = "loyalty_points_earned")
  private int loyaltyPointsEarned;

  public String getDiscountCode() { return discountCode; }
  public void setDiscountCode(String v) { this.discountCode = v; }
  public String getGiftCodeHash() { return giftCodeHash; }
  public void setGiftCodeHash(String v) { this.giftCodeHash = v; }
  public long getGiftAppliedMinor() { return giftAppliedMinor; }
  public void setGiftAppliedMinor(long v) { this.giftAppliedMinor = v; }
  public int getLoyaltyPointsUsed() { return loyaltyPointsUsed; }
  public void setLoyaltyPointsUsed(int v) { this.loyaltyPointsUsed = v; }
  public int getLoyaltyPointsEarned() { return loyaltyPointsEarned; }
  public void setLoyaltyPointsEarned(int v) { this.loyaltyPointsEarned = v; }

  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderLine> lines = new ArrayList<>();

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public long getOrderNumber() { return orderNumber; }
  public void setOrderNumber(long v) { this.orderNumber = v; }
  public Customer getCustomer() { return customer; }
  public void setCustomer(Customer c) { this.customer = c; }
  public Location getLocation() { return location; }
  public void setLocation(Location l) { this.location = l; }
  public String getEmail() { return email; }
  public void setEmail(String v) { this.email = v; }
  public String getPhone() { return phone; }
  public void setPhone(String v) { this.phone = v; }
  public String getFinancialStatus() { return financialStatus; }
  public void setFinancialStatus(String v) { this.financialStatus = v; }
  public String getFulfillmentStatus() { return fulfillmentStatus; }
  public void setFulfillmentStatus(String v) { this.fulfillmentStatus = v; }
  public long getSubtotalMinor() { return subtotalMinor; }
  public void setSubtotalMinor(long v) { this.subtotalMinor = v; }
  public long getDiscountMinor() { return discountMinor; }
  public void setDiscountMinor(long v) { this.discountMinor = v; }
  public long getShippingMinor() { return shippingMinor; }
  public void setShippingMinor(long v) { this.shippingMinor = v; }
  public long getTaxMinor() { return taxMinor; }
  public void setTaxMinor(long v) { this.taxMinor = v; }
  public long getTotalMinor() { return totalMinor; }
  public void setTotalMinor(long v) { this.totalMinor = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { this.currency = v; }
  public String getPaymentMethod() { return paymentMethod; }
  public void setPaymentMethod(String v) { this.paymentMethod = v; }
  public String getSource() { return source; }
  public void setSource(String v) { this.source = v; }
  public String getNotes() { return notes; }
  public void setNotes(String v) { this.notes = v; }
  public String getBillingAddress() { return billingAddress; }
  public void setBillingAddress(String v) { this.billingAddress = v; }
  public String getShippingAddress() { return shippingAddress; }
  public void setShippingAddress(String v) { this.shippingAddress = v; }
  public LocalDateTime getProcessedAt() { return processedAt; }
  public void setProcessedAt(LocalDateTime v) { this.processedAt = v; }
  public LocalDateTime getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(LocalDateTime v) { this.cancelledAt = v; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
  public List<OrderLine> getLines() { return lines; }
}
