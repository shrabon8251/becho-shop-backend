package com.becho.saas.modules.customers.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;

@Entity
@Table(name = "customer_addresses",
    indexes = @Index(name = "idx_caddr_customer", columnList = "customer_id"))
public class CustomerAddress extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(length = 64)
  private String label;

  @Column(name = "first_name", length = 64)
  private String firstName;

  @Column(name = "last_name", length = 64)
  private String lastName;

  @Column(length = 128)
  private String company;

  @Column(nullable = false, length = 255)
  private String address1;

  @Column(length = 255)
  private String address2;

  @Column(nullable = false, length = 128)
  private String city;

  @Column(length = 128)
  private String province;

  @Column(nullable = false, length = 2)
  private String country = "BD";

  @Column(name = "postal_code", length = 32)
  private String postalCode;

  @Column(length = 32)
  private String phone;

  @Column(name = "is_default_shipping", nullable = false)
  private boolean defaultShipping;

  @Column(name = "is_default_billing", nullable = false)
  private boolean defaultBilling;

  public Customer getCustomer() { return customer; }
  public void setCustomer(Customer c) { this.customer = c; }
  public Store getStore() { return store; }
  public void setStore(Store s) { this.store = s; }
  public String getLabel() { return label; }
  public void setLabel(String v) { this.label = v; }
  public String getFirstName() { return firstName; }
  public void setFirstName(String v) { this.firstName = v; }
  public String getLastName() { return lastName; }
  public void setLastName(String v) { this.lastName = v; }
  public String getCompany() { return company; }
  public void setCompany(String v) { this.company = v; }
  public String getAddress1() { return address1; }
  public void setAddress1(String v) { this.address1 = v; }
  public String getAddress2() { return address2; }
  public void setAddress2(String v) { this.address2 = v; }
  public String getCity() { return city; }
  public void setCity(String v) { this.city = v; }
  public String getProvince() { return province; }
  public void setProvince(String v) { this.province = v; }
  public String getCountry() { return country; }
  public void setCountry(String v) { this.country = v; }
  public String getPostalCode() { return postalCode; }
  public void setPostalCode(String v) { this.postalCode = v; }
  public String getPhone() { return phone; }
  public void setPhone(String v) { this.phone = v; }
  public boolean isDefaultShipping() { return defaultShipping; }
  public void setDefaultShipping(boolean v) { this.defaultShipping = v; }
  public boolean isDefaultBilling() { return defaultBilling; }
  public void setDefaultBilling(boolean v) { this.defaultBilling = v; }
}
