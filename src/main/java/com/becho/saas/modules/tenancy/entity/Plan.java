package com.becho.saas.modules.tenancy.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plans")
public class Plan extends BaseEntity {

  @Column(nullable = false, length = 64)
  private String name;

  @Column(name = "price_minor", nullable = false)
  private long priceMinor;

  @Column(name = "`interval`", nullable = false, length = 16)
  private String interval = "monthly";

  @Column(name = "trial_days", nullable = false)
  private int trialDays = 14;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String limits;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String features;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public long getPriceMinor() { return priceMinor; }
  public void setPriceMinor(long v) { this.priceMinor = v; }
  public String getInterval() { return interval; }
  public void setInterval(String v) { this.interval = v; }
  public int getTrialDays() { return trialDays; }
  public void setTrialDays(int v) { this.trialDays = v; }
  public String getLimits() { return limits; }
  public void setLimits(String v) { this.limits = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { this.active = v; }
}
