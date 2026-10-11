package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.catalog.entity.Bundle;
import com.becho.saas.modules.catalog.repository.BundleRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundle endpoints: GET/POST /stores/{storeId}/bundles,
 * PUT/DELETE /stores/{storeId}/bundles/{id}.
 * Frontend: { id, name, items: [{productId, variantId?, qty}], price, image?, active }
 * (price in major units; stored as price_minor).
 */
@RestController
@RequestMapping("/stores/{storeId}/bundles")
public class StoreBundleController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final BundleRepository bundles;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreBundleController(BundleRepository bundles,
                                StoreRepository stores, StoreScope scope) {
    this.bundles = bundles;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return bundles.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    Bundle b = new Bundle();
    b.setStore(store);
    applyBody(b, body);
    return toFrontend(bundles.save(b));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Bundle b;
    if (StoreScope.isNewId(id)) {
      Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
      b = new Bundle();
      b.setStore(store);
    } else {
      b = bundles.findByIdAndStoreId(StoreScope.lid(id), sid)
          .orElseThrow(() -> ApiException.notFound("Bundle"));
    }
    applyBody(b, body);
    return toFrontend(bundles.save(b));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Bundle b = bundles.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Bundle"));
    bundles.delete(b);
  }

  private void applyBody(Bundle b, Map<String, Object> body) {
    if (body.get("name") != null) b.setName(String.valueOf(body.get("name")));
    if (body.get("items") != null) b.setItems(toJson(body.get("items")));
    if (body.get("price") instanceof Number n) {
      b.setPriceMinor(Math.round(n.doubleValue() * 100));
    }
    if (body.get("image") != null) b.setImage(String.valueOf(body.get("image")));
    if (body.get("active") instanceof Boolean act) b.setActive(act);
  }

  private String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception e) {
      return "[]";
    }
  }

  private Map<String, Object> toFrontend(Bundle b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", b.getId().toString());
    m.put("name", b.getName());
    m.put("items", fromJson(b.getItems()));
    m.put("price", b.getPriceMinor() / 100.0);
    m.put("image", b.getImage());
    m.put("active", b.isActive());
    return m;
  }

  private Object fromJson(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return MAPPER.readValue(json, List.class);
    } catch (Exception e) {
      return List.of();
    }
  }
}
