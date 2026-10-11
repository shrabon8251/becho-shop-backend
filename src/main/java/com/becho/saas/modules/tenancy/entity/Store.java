package com.becho.saas.modules.tenancy.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stores")
public class Store extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 64)
  private String slug;

  @Column(length = 255, unique = true)
  private String domain;

  @Column(length = 64, unique = true)
  private String subdomain;

  @Column(nullable = false, length = 3)
  private String currency = "BDT";

  @Column(nullable = false, length = 64)
  private String timezone = "Asia/Dhaka";

  @Column(nullable = false, length = 32)
  private String status = "active";

  @Column(name = "plan_name", length = 32)
  private String planName;

  @Column(name = "billing_cycle", length = 16)
  private String billingCycle;

  @Column(name = "billing_status", length = 24, nullable = false)
  private String billingStatus = "active";

  @Column(name = "trial_ends_at")
  private java.time.LocalDateTime trialEndsAt;

  public java.time.LocalDateTime getTrialEndsAt() { return trialEndsAt; }
  public void setTrialEndsAt(java.time.LocalDateTime trialEndsAt) { this.trialEndsAt = trialEndsAt; }

  @Column(name = "billing_method_kind", length = 16)
  private String billingMethodKind;

  @Column(name = "billing_method_label", length = 64)
  private String billingMethodLabel;

  @Column(name = "billing_method_trxid", length = 64)
  private String billingMethodTrxid;

  @Column(name = "billing_trx_id", length = 64)
  private String billingTrxId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String settings;

  public Tenant getTenant() { return tenant; }
  public void setTenant(Tenant tenant) { this.tenant = tenant; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public String getDomain() { return domain; }
  public void setDomain(String domain) { this.domain = domain; }
  public String getSubdomain() { return subdomain; }
  public void setSubdomain(String subdomain) { this.subdomain = subdomain; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getTimezone() { return timezone; }
  public void setTimezone(String timezone) { this.timezone = timezone; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getPlanName() { return planName; }
  public void setPlanName(String planName) { this.planName = planName; }
  public String getBillingCycle() { return billingCycle; }
  public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }
  public String getBillingStatus() { return billingStatus; }
  public void setBillingStatus(String billingStatus) { this.billingStatus = billingStatus; }
  public String getBillingMethodKind() { return billingMethodKind; }
  public void setBillingMethodKind(String billingMethodKind) { this.billingMethodKind = billingMethodKind; }
  public String getBillingMethodLabel() { return billingMethodLabel; }
  public void setBillingMethodLabel(String billingMethodLabel) { this.billingMethodLabel = billingMethodLabel; }
  public String getBillingMethodTrxid() { return billingMethodTrxid; }
  public void setBillingMethodTrxid(String billingMethodTrxid) { this.billingMethodTrxid = billingMethodTrxid; }
  public String getBillingTrxId() { return billingTrxId; }
  public void setBillingTrxId(String billingTrxId) { this.billingTrxId = billingTrxId; }
  public String getSettings() { return settings; }
  public void setSettings(String settings) { this.settings = settings; }
}
