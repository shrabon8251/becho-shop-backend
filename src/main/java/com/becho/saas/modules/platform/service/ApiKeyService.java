package com.becho.saas.modules.platform.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.ApiKey;
import com.becho.saas.modules.platform.repository.ApiKeyRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * API keys: raw key exists only at creation (shown once).
 * Verification compares SHA-256 hashes — the DB never holds a usable key.
 */
@Service
public class ApiKeyService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final ApiKeyRepository keys;
  private final StoreRepository stores;

  public ApiKeyService(ApiKeyRepository keys, StoreRepository stores) {
    this.keys = keys;
    this.stores = stores;
  }

  public record IssuedKey(Long id, String rawKey, String keyPrefix) {}

  @Transactional
  public IssuedKey issue(String name, String scopesJson, LocalDateTime expiresAt) {
    Long storeId = TenantContext.requireStoreId();
    Store store = stores.findById(storeId).orElseThrow(() -> ApiException.notFound("Store"));
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    String rawKey = "becho_" + HexFormat.of().formatHex(raw);
    ApiKey k = new ApiKey();
    k.setStore(store);
    k.setName(name);
    k.setKeyPrefix(rawKey.substring(0, 12));
    k.setKeyHash(sha256(rawKey));
    k.setScopes(scopesJson);
    k.setExpiresAt(expiresAt);
    keys.save(k);
    return new IssuedKey(k.getId(), rawKey, k.getKeyPrefix());
  }

  @Transactional
  public ApiKey verify(String rawKey) {
    ApiKey k = keys.findByKeyHash(sha256(rawKey))
        .orElseThrow(() -> ApiException.forbidden());
    if (!"active".equals(k.getStatus())) throw ApiException.forbidden();
    if (k.getExpiresAt() != null && LocalDateTime.now().isAfter(k.getExpiresAt())) {
      throw ApiException.forbidden();
    }
    k.setLastUsedAt(LocalDateTime.now());
    return keys.save(k);
  }

  public static String sha256(String s) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
