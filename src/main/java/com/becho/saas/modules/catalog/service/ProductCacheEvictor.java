package com.becho.saas.modules.catalog.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Store-scoped eviction for the "product" cache.
 *
 * <p>Cache keys are {@code list:{storeId}:...} and {@code get:{storeId}:...}
 * inside the {@code product} cache (stored as {@code product::<key>} in
 * Redis). Evicting by store prefix avoids clearing every store's cache
 * on a single product write.
 */
@Service
public class ProductCacheEvictor {

  private final StringRedisTemplate redis;

  public ProductCacheEvictor(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /** Evicts all cached product entries for one store. Best-effort. */
  public void evictStore(Long storeId) {
    try {
      Set<String> listKeys = redis.keys("product::list:" + storeId + ":*");
      if (listKeys != null && !listKeys.isEmpty()) {
        redis.delete(listKeys);
      }
      Set<String> getKeys = redis.keys("product::get:" + storeId + ":*");
      if (getKeys != null && !getKeys.isEmpty()) {
        redis.delete(getKeys);
      }
    } catch (Exception e) {
      // Cache eviction is best-effort; TTL will clean up anyway.
    }
  }
}
