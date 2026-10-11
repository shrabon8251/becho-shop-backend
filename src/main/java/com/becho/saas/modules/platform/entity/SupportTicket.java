package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Support ticket between a merchant and platform support.
 * Replies JSON: [{from: "merchant"|"support", text, at}, ...].
 */
@Entity
@Table(name = "support_tickets",
    indexes = @Index(name = "idx_tickets_store", columnList = "store_id,status,created_at"))
public class SupportTicket extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 255)
  private String subject;

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String message;

  @Column(nullable = false, length = 32)
  private String status = "open";

  @Column(name = "customer_email", length = 255)
  private String customerEmail;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String replies;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getSubject() { return subject; }
  public void setSubject(String v) { this.subject = v; }
  public String getMessage() { return message; }
  public void setMessage(String v) { this.message = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getCustomerEmail() { return customerEmail; }
  public void setCustomerEmail(String v) { this.customerEmail = v; }
  public String getReplies() { return replies; }
  public void setReplies(String v) { this.replies = v; }
}
