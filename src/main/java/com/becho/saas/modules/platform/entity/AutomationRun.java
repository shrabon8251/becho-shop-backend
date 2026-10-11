package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/**
 * A queued automation email. Created when a trigger event fires;
 * sent by the scheduled AutomationRunner once scheduledFor elapses.
 */
@Entity
@Table(name = "automation_runs",
    indexes = @Index(name = "idx_autorun_due", columnList = "store_id,status,scheduled_for"))
public class AutomationRun extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "automation_id", nullable = false)
  private Automation automation;

  @Column(name = "`trigger`", nullable = false, length = 32)
  private String trigger;

  @Column(name = "recipient_email", nullable = false, length = 255)
  private String recipientEmail;

  @Column(name = "recipient_name", length = 255)
  private String recipientName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String vars;

  @Column(name = "scheduled_for", nullable = false)
  private LocalDateTime scheduledFor;

  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  @Column(nullable = false, length = 16)
  private String status = "pending";

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public Automation getAutomation() { return automation; }
  public void setAutomation(Automation a) { this.automation = a; }
  public String getTrigger() { return trigger; }
  public void setTrigger(String v) { this.trigger = v; }
  public String getRecipientEmail() { return recipientEmail; }
  public void setRecipientEmail(String v) { this.recipientEmail = v; }
  public String getRecipientName() { return recipientName; }
  public void setRecipientName(String v) { this.recipientName = v; }
  public String getVars() { return vars; }
  public void setVars(String v) { this.vars = v; }
  public LocalDateTime getScheduledFor() { return scheduledFor; }
  public void setScheduledFor(LocalDateTime v) { this.scheduledFor = v; }
  public LocalDateTime getSentAt() { return sentAt; }
  public void setSentAt(LocalDateTime v) { this.sentAt = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
}
