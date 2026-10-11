package com.becho.saas.modules.tenancy.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, unique = true, length = 64)
  private String slug;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_id")
  private Plan plan;

  @Column(nullable = false, length = 32)
  private String status = "active";

  @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Store> stores = new ArrayList<>();

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public Plan getPlan() { return plan; }
  public void setPlan(Plan plan) { this.plan = plan; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public List<Store> getStores() { return stores; }
}
