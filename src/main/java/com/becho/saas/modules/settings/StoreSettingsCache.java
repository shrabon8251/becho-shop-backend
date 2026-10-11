package com.becho.saas.modules.settings;

import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Cached access to store settings JSON. Settings change rarely but are
 * read on every order, notification, and fraud check — caching avoids
 * a DB hit per request. Evicted on settings save.
 */
@Service
public class StoreSettingsCache {

  private final StoreRepository stores;

  public StoreSettingsCache(StoreRepository stores) {
    this.stores = stores;
  }

  /** Returns the raw settings JSON for a store (cached 30 min). */
  @Cacheable(value = "store-settings", key = "#storeId")
  public String getSettings(Long storeId) {
    return stores.findById(storeId)
        .map(s -> s.getSettings())
        .orElse(null);
  }

  /** Evicts the cached settings for a store (call after save). */
  @CacheEvict(value = "store-settings", key = "#storeId")
  public void evict(Long storeId) {
  }
}
