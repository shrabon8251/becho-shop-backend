package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.Automation;
import com.becho.saas.modules.platform.repository.AutomationRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Automation endpoints: GET/POST /stores/{storeId}/automations,
 * PUT/DELETE /stores/{storeId}/automations/{id}.
 * Frontend: { id, name, trigger, active, delayHours, subject, body }.
 */
@RestController
@RequestMapping("/stores/{storeId}/automations")
public class StoreAutomationController {

  private static final Set<String> TRIGGERS =
      Set.of("abandoned_checkout", "welcome", "winback");

  private final AutomationRepository automations;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreAutomationController(AutomationRepository automations,
                                    StoreRepository stores, StoreScope scope) {
    this.automations = automations;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return automations.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    Automation a = new Automation();
    a.setStore(store);
    applyBody(a, body);
    return toFrontend(automations.save(a));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Automation a;
    if (StoreScope.isNewId(id)) {
      Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
      a = new Automation();
      a.setStore(store);
    } else {
      a = automations.findByIdAndStoreId(StoreScope.lid(id), sid)
          .orElseThrow(() -> ApiException.notFound("Automation"));
    }
    applyBody(a, body);
    return toFrontend(automations.save(a));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Automation a = automations.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Automation"));
    automations.delete(a);
  }

  private void applyBody(Automation a, Map<String, Object> body) {
    if (body.get("name") != null) a.setName(String.valueOf(body.get("name")));
    if (body.get("trigger") != null) {
      String t = String.valueOf(body.get("trigger"));
      if (!TRIGGERS.contains(t)) {
        throw ApiException.badRequest("BAD_TRIGGER", "Unknown trigger: " + t);
      }
      a.setTrigger(t);
    }
    if (body.get("active") instanceof Boolean b) a.setActive(b);
    if (body.get("delayHours") instanceof Number n) a.setDelayHours(n.intValue());
    if (body.get("subject") != null) a.setSubject(String.valueOf(body.get("subject")));
    if (body.get("body") != null) a.setBody(String.valueOf(body.get("body")));
  }

  private Map<String, Object> toFrontend(Automation a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId().toString());
    m.put("name", a.getName());
    m.put("trigger", a.getTrigger());
    m.put("active", a.isActive());
    m.put("delayHours", a.getDelayHours());
    m.put("subject", a.getSubject());
    m.put("body", a.getBody());
    return m;
  }
}
