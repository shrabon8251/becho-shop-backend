package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.StoreAppsConfig;
import com.becho.saas.modules.platform.repository.StoreAppsConfigRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Frontend-compatible apps config: GET/PUT /stores/{storeId}/apps.
 * Shape: { bar: { on, text }, reviews: { on }, installed?: string[] }.
 */
@RestController
@RequestMapping("/stores/{storeId}/apps")
public class StoreAppsController {

  private static final String DEFAULT_CONFIG =
      "{\"bar\":{\"on\":false,\"text\":\"Sale: 10% off with code WELCOME10\"},"
          + "\"reviews\":{\"on\":false},\"installed\":[]}";

  private final StoreAppsConfigRepository configs;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreAppsController(StoreAppsConfigRepository configs, StoreRepository stores,
                             StoreScope scope) {
    this.configs = configs;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public String get(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return configs.findById(sid).map(StoreAppsConfig::getConfig).orElse(DEFAULT_CONFIG);
  }

  @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public String save(@PathVariable String storeId, @RequestBody String rawJson) {
    Long sid = scope.require(storeId);
    if (rawJson == null || rawJson.isBlank() || rawJson.length() > 100_000) {
      throw ApiException.badRequest("BAD_CONFIG", "Invalid apps config");
    }
    StoreAppsConfig cfg = configs.findById(sid).orElseGet(() -> {
      StoreAppsConfig c = new StoreAppsConfig();
      c.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
      return c;
    });
    cfg.setConfig(rawJson);
    configs.save(cfg);
    return rawJson;
  }
}
