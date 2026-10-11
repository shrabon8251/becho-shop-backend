package com.becho.saas.modules.shipping.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tax_rates",
    indexes = @Index(name = "idx_taxrates_store", columnList = "store_id,country"))
public class TaxRate extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 2)
  private String country = "BD";

  @Column(name = "region", length = 128)
  private String province;

  /** Percent: 15.00 = 15% */
  @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal ratePercent = BigDecimal.ZERO;

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getCountry() { return country; }
  public void setCountry(String v) { this.country = v; }
  public String getProvince() { return province; }
  public void setProvince(String v) { this.province = v; }
  public BigDecimal getRatePercent() { return ratePercent; }
  public void setRatePercent(BigDecimal v) { this.ratePercent = v; }
  /** Basis points convenience: 1500 = 15%. */
  public int getRateBps() { return ratePercent.movePointRight(2).intValueExact(); }
}
