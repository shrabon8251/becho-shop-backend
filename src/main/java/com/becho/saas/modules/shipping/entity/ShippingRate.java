package com.becho.saas.modules.shipping.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipping_rates",
    indexes = @Index(name = "idx_rates_zone", columnList = "zone_id"))
public class ShippingRate extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "zone_id", nullable = false)
  private ShippingZone zone;

  @Column(nullable = false, length = 128)
  private String name;

  /** flat | weight_based | price_based */
  @Column(name = "rate_type", nullable = false, length = 32)
  private String rateType = "flat";

  @Column(name = "price_minor", nullable = false)
  private long priceMinor;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String conditions;

  @Column(name = "min_days")
  private Integer minDays;

  @Column(name = "max_days")
  private Integer maxDays;

  public ShippingZone getZone() { return zone; }
  public void setZone(ShippingZone z) { this.zone = z; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getRateType() { return rateType; }
  public void setRateType(String v) { this.rateType = v; }
  public long getPriceMinor() { return priceMinor; }
  public void setPriceMinor(long v) { this.priceMinor = v; }
  public String getConditions() { return conditions; }
  public void setConditions(String v) { this.conditions = v; }
  public Integer getMinDays() { return minDays; }
  public void setMinDays(Integer v) { this.minDays = v; }
  public Integer getMaxDays() { return maxDays; }
  public void setMaxDays(Integer v) { this.maxDays = v; }
}
