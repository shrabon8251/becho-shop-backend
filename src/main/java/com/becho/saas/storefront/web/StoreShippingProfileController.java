package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.shipping.entity.ShippingProfile;
import com.becho.saas.modules.shipping.repository.ShippingProfileRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Delivery profile endpoints: GET/POST /stores/{storeId}/shippingProfiles,
 * PUT/DELETE /stores/{storeId}/shippingProfiles/{id}.
 * Note the camelCase URL segment, matching the frontend's http.ts.
 * Frontend DeliveryProfile: { id, name, productIds: string[], zoneIds: string[] }.
 */
@RestController
@RequestMapping("/stores/{storeId}/shippingProfiles")
public class StoreShippingProfileController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ShippingProfileRepository profiles;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreShippingProfileController(ShippingProfileRepository profiles,
                                         StoreRepository stores, StoreScope scope) {
    this.profiles = profiles;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return profiles.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    ShippingProfile p = new ShippingProfile();
    p.setStore(store);
    applyBody(p, body);
    return toFrontend(profiles.save(p));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    ShippingProfile p;
    if (StoreScope.isNewId(id)) {
      Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
      p = new ShippingProfile();
      p.setStore(store);
    } else {
      p = profiles.findByIdAndStoreId(StoreScope.lid(id), sid)
          .orElseThrow(() -> ApiException.notFound("ShippingProfile"));
    }
    applyBody(p, body);
    return toFrontend(profiles.save(p));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    ShippingProfile p = profiles.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("ShippingProfile"));
    profiles.delete(p);
  }

  private void applyBody(ShippingProfile p, Map<String, Object> body) {
    if (body.get("name") != null) p.setName(String.valueOf(body.get("name")));
    // Frontend sends productIds/zoneIds arrays; persist as zones JSON.
    Map<String, Object> zones = new LinkedHashMap<>();
    if (body.get("productIds") != null) zones.put("productIds", body.get("productIds"));
    if (body.get("zoneIds") != null) zones.put("zoneIds", body.get("zoneIds"));
    if (body.get("zones") != null) zones.put("zones", body.get("zones"));
    if (!zones.isEmpty()) p.setZones(toJson(zones));
    if (body.get("active") instanceof Boolean b) p.setActive(b);
  }

  private String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception e) {
      return "{}";
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toFrontend(ShippingProfile p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.getId().toString());
    m.put("name", p.getName());
    Map<String, Object> zones = new LinkedHashMap<>();
    if (p.getZones() != null && !p.getZones().isBlank()) {
      try {
        zones = MAPPER.readValue(p.getZones(), Map.class);
      } catch (Exception ignored) { /* keep empty */ }
    }
    m.put("productIds", zones.getOrDefault("productIds", List.of()));
    m.put("zoneIds", zones.getOrDefault("zoneIds", List.of()));
    return m;
  }
}
