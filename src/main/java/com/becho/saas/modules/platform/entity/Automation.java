package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;

/**
 * Store automation rule: trigger-based email flows
 * (abandoned_checkout | welcome | winback).
 */
@Entity
@Table(name = "automations",
    indexes = @Index(name = "idx_automations_store", columnList = "store_id,active"))
public class Automation extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "`trigger`", nullable = false, length = 32)
  private String trigger;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "delay_hours", nullable = false)
  private int delayHours = 24;

  @Column(nullable = false, length = 255)
  private String subject;

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String body;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getTrigger() { return trigger; }
  public void setTrigger(String v) { this.trigger = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { this.active = v; }
  public int getDelayHours() { return delayHours; }
  public void setDelayHours(int v) { this.delayHours = v; }
  public String getSubject() { return subject; }
  public void setSubject(String v) { this.subject = v; }
  public String getBody() { return body; }
  public void setBody(String v) { this.body = v; }
}
