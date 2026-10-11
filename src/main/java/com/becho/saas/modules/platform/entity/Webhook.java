package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhooks",
    indexes = @Index(name = "idx_webhooks_store", columnList = "store_id,status"))
public class Webhook extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  /** orders/create | orders/paid | products/update ... */
  @Column(nullable = false, length = 64)
  private String topic;

  @Column(name = "url", nullable = false, length = 1024)
  private String targetUrl;

  /** SHA-256 of the signing secret — raw secret shown once at creation. */
  @Column(name = "secret_hash", nullable = false, length = 64)
  private String secretHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String headers;

  @Column(nullable = false, length = 32)
  private String status = "active";

  @Column(name = "failure_count", nullable = false)
  private int failureCount;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getTopic() { return topic; }
  public void setTopic(String v) { this.topic = v; }
  public String getTargetUrl() { return targetUrl; }
  public void setTargetUrl(String v) { this.targetUrl = v; }
  public String getSecretHash() { return secretHash; }
  public void setSecretHash(String v) { this.secretHash = v; }
  public String getHeaders() { return headers; }
  public void setHeaders(String v) { this.headers = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public int getFailureCount() { return failureCount; }
  public void setFailureCount(int v) { this.failureCount = v; }
}
