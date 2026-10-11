package com.becho.saas.modules.shipping.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shipping_zones",
    indexes = @Index(name = "idx_zones_store", columnList = "store_id"))
public class ShippingZone extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false, length = 128)
  private String name;

  /** JSON array of {country, province} matchers. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String countries;

  @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ShippingRate> rates = new ArrayList<>();

  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getCountries() { return countries; }
  public void setCountries(String v) { this.countries = v; }
  public List<ShippingRate> getRates() { return rates; }
}
