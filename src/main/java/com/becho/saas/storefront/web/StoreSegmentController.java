package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.customers.entity.CustomerSegment;
import com.becho.saas.modules.customers.repository.CustomerSegmentRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer segment endpoints: GET/POST /stores/{storeId}/segments,
 * PUT/DELETE /stores/{storeId}/segments/{id}.
 * Frontend CustomerSegment: { id, name, conditions: [{field, op, value}] }.
 */
@RestController
@RequestMapping("/stores/{storeId}/segments")
public class StoreSegmentController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CustomerSegmentRepository segments;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreSegmentController(CustomerSegmentRepository segments,
                                 StoreRepository stores, StoreScope scope) {
    this.segments = segments;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return segments.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    CustomerSegment s = new CustomerSegment();
    s.setStore(store);
    applyBody(s, body);
    return toFrontend(segments.save(s));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    CustomerSegment s;
    if (StoreScope.isNewId(id)) {
      Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
      s = new CustomerSegment();
      s.setStore(store);
    } else {
      s = segments.findByIdAndStoreId(StoreScope.lid(id), sid)
          .orElseThrow(() -> ApiException.notFound("Segment"));
    }
    applyBody(s, body);
    return toFrontend(segments.save(s));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    CustomerSegment s = segments.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Segment"));
    segments.delete(s);
  }

  private void applyBody(CustomerSegment s, Map<String, Object> body) {
    if (body.get("name") != null) s.setName(String.valueOf(body.get("name")));
    Object conditions = body.get("conditions");
    if (conditions == null) conditions = body.get("rules");
    if (conditions != null) s.setRules(toJson(conditions));
  }

  private String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception e) {
      return "[]";
    }
  }

  private Map<String, Object> toFrontend(CustomerSegment s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId().toString());
    m.put("name", s.getName());
    m.put("conditions", fromJson(s.getRules()));
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
