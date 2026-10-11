package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.modules.wallet.entity.LoyaltyAccount;
import com.becho.saas.modules.wallet.repository.LoyaltyAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Frontend-compatible loyalty endpoints: GET/PUT /stores/{storeId}/loyalty.
 * Settings are stored in the store's settings JSON (key "loyalty") —
 * no in-memory state; per-customer points come from the LoyaltyAccount table.
 */
@RestController
@RequestMapping("/stores/{storeId}/loyalty")
public class StoreLoyaltyController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final StoreScope scope;
  private final LoyaltyAccountRepository loyaltyAccounts;
  private final StoreRepository stores;

  public StoreLoyaltyController(StoreScope scope,
                                LoyaltyAccountRepository loyaltyAccounts,
                                StoreRepository stores) {
    this.scope = scope;
    this.loyaltyAccounts = loyaltyAccounts;
    this.stores = stores;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public Map<String, Object> get(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    var store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    return readLoyalty(store.getSettings());
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                  @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    var store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    Map<String, Object> settings = new LinkedHashMap<>(body);
    store.setSettings(writeLoyalty(store.getSettings(), settings));
    stores.save(store);
    return settings;
  }

  private Map<String, Object> readLoyalty(String settingsJson) {
    try {
      JsonNode root = settingsJson == null || settingsJson.isBlank()
          ? MAPPER.createObjectNode() : MAPPER.readTree(settingsJson);
      JsonNode loyalty = root.path("loyalty");
      if (loyalty.isObject()) {
        return MAPPER.convertValue(loyalty, Map.class);
      }
    } catch (Exception ignored) {
    }
    return defaultSettings();
  }

  private String writeLoyalty(String settingsJson, Map<String, Object> loyalty) {
    try {
      ObjectNode root = settingsJson == null || settingsJson.isBlank()
          ? MAPPER.createObjectNode()
          : (ObjectNode) MAPPER.readTree(settingsJson);
      root.set("loyalty", MAPPER.valueToTree(loyalty));
      return MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to save loyalty settings", e);
    }
  }

  private Map<String, Object> defaultSettings() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("enabled", false);
    m.put("earnRate", 1);
    m.put("redeemRate", 100);
    m.put("tiers", new java.util.ArrayList<>());
    return m;
  }

  /** Surface a customer's loyalty account (used by customer endpoints). */
  public Map<String, Object> accountFor(Long customerId) {
    return loyaltyAccounts.findById(customerId).map(a -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("points", a.getPoints());
      m.put("lifetimePoints", a.getLifetimePoints());
      m.put("tier", a.getTier());
      return m;
    }).orElseGet(LinkedHashMap::new);
  }
}
