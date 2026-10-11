package com.becho.saas.tenancy;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Maps a storefront domain/subdomain to its store id. Result is cached. */
@Component
public class StorefrontResolver {

  private final JdbcTemplate jdbc;

  public StorefrontResolver(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Cacheable(value = "storefront-domain", key = "#domain")
  public Long resolve(String domain) {
    try {
      return jdbc.queryForObject(
          "SELECT id FROM stores WHERE (domain = ? OR subdomain = ?) AND status = 'active' AND deleted_at IS NULL LIMIT 1",
          Long.class, domain, domain);
    } catch (Exception e) {
      return null;
    }
  }
}
