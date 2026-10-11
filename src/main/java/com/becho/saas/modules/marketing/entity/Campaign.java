package com.becho.saas.modules.marketing.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns", indexes = @Index(name = "idx_campaigns_store", columnList = "store_id,status"))
public class Campaign extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  /** email|sms (frontend maps Email/Social/Google Ads to email) */
  @Column(nullable = false, length = 16)
  private String channel = "email";

  /** draft|scheduled|sending|sent|cancelled */
  @Column(nullable = false, length = 32)
  private String status = "draft";

  @Column(length = 255)
  private String subject;

  @Column(columnDefinition = "MEDIUMTEXT")
  private String body;

  @Column(columnDefinition = "json")
  private String audience;

  @Column(name = "scheduled_at")
  private LocalDateTime scheduledAt;

  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getChannel() { return channel; }
  public void setChannel(String v) { this.channel = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getSubject() { return subject; }
  public void setSubject(String v) { this.subject = v; }
  public String getBody() { return body; }
  public void setBody(String v) { this.body = v; }
  public String getAudience() { return audience; }
  public void setAudience(String v) { this.audience = v; }
  public LocalDateTime getScheduledAt() { return scheduledAt; }
  public void setScheduledAt(LocalDateTime v) { this.scheduledAt = v; }
  public LocalDateTime getSentAt() { return sentAt; }
  public void setSentAt(LocalDateTime v) { this.sentAt = v; }
}
