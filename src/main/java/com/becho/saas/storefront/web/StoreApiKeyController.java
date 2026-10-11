package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.ApiKey;
import com.becho.saas.modules.platform.repository.ApiKeyRepository;
import com.becho.saas.modules.platform.service.ApiKeyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible API key endpoints: /stores/{storeId}/api-keys.
 * GET list never exposes secrets (only the key prefix); POST create returns
 * the raw key ONCE; DELETE revokes.
 */
@RestController
@RequestMapping("/stores/{storeId}/api-keys")
public class StoreApiKeyController {

  private final ApiKeyService apiKeyService;
  private final ApiKeyRepository apiKeys;
  private final StoreScope scope;

  public StoreApiKeyController(ApiKeyService apiKeyService,
                                ApiKeyRepository apiKeys, StoreScope scope) {
    this.apiKeyService = apiKeyService;
    this.apiKeys = apiKeys;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return apiKeys.findByStoreIdOrderByCreatedAtDesc(sid)
        .stream().map(this::toFrontend).collect(Collectors.toList());
  }

  /**
   * Issues a new key. The raw key is returned ONCE in the response — it is
   * never persisted (only its SHA-256 hash) and cannot be recovered later.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public Map<String, Object> create(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    scope.require(storeId);
    // ApiKeyService.issue() resolves the store from the JWT tenant, which
    // scope.require() already validated against the URL storeId.
    String name = body.get("name") == null ? "API key" : (String) body.get("name");
    ApiKeyService.IssuedKey issued = apiKeyService.issue(name, null, null);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", issued.id().toString());
    m.put("name", name);
    m.put("key", issued.rawKey());
    m.put("keyPrefix", issued.keyPrefix());
    return m;
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void revoke(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    ApiKey k = apiKeys.findById(StoreScope.lid(id))
        .filter(a -> a.getStore() != null && sid.equals(a.getStore().getId()))
        .orElseThrow(() -> ApiException.notFound("ApiKey"));
    k.setStatus("revoked");
    apiKeys.save(k);
  }

  private Map<String, Object> toFrontend(ApiKey k) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", k.getId().toString());
    m.put("name", k.getName());
    // Never the raw key or its hash — only the non-secret prefix for display.
    m.put("key", k.getKeyPrefix());
    m.put("createdAt", k.getCreatedAt() == null ? null
        : k.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
    m.put("lastUsed", k.getLastUsedAt() == null ? null
        : k.getLastUsedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
    return m;
  }
}
