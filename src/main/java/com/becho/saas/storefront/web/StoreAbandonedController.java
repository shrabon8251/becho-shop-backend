package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.marketing.entity.AbandonedCheckout;
import com.becho.saas.modules.marketing.repository.AbandonedCheckoutRepository;
import com.becho.saas.modules.platform.service.AutomationService;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible abandoned checkout endpoints: /stores/{storeId}/abandoned-checkouts[/{id}/recover].
 * DB-backed. Frontend: { id, email, name?, items, createdAt, recovered }.
 */
@RestController
@RequestMapping("/stores/{storeId}/abandoned-checkouts")
public class StoreAbandonedController {

  private final AbandonedCheckoutRepository abandoned;
  private final StoreRepository stores;
  private final StoreScope scope;

  private final AutomationService automations;

  public StoreAbandonedController(AbandonedCheckoutRepository abandoned,
                                   StoreRepository stores, StoreScope scope,
                                   AutomationService automations) {
    this.abandoned = abandoned;
    this.stores = stores;
    this.scope = scope;
    this.automations = automations;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return abandoned.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping("/{id}/recover")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> recover(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    AbandonedCheckout a = abandoned.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("AbandonedCheckout"));
    // Backend SENDS the recovery email via automation, then marks recovered.
    if (a.getEmail() != null && !a.getEmail().isBlank()) {
      automations.fire(sid, "abandoned_checkout", a.getEmail(), "",
          Map.of("email", a.getEmail(), "amount", String.valueOf(a.getTotalMinor())));
    }
    a.setRecoveredAt(LocalDateTime.now());
    return toFrontend(abandoned.save(a));
  }

  /** Internal hook: record an abandoned checkout (called by checkout flow later). */
  public Map<String, Object> record(String storeId, Map<String, Object> body) {
    Long sid = scope.require(storeId);
    AbandonedCheckout a = new AbandonedCheckout();
    a.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    if (body.get("email") != null) a.setEmail(String.valueOf(body.get("email")));
    if (body.get("cart") != null) a.setCart(String.valueOf(body.get("cart")));
    else if (body.get("items") != null) a.setCart(String.valueOf(body.get("items")));
    Object total = body.get("totalMinor");
    if (total instanceof Number n) a.setTotalMinor(n.longValue());
    Object subtotal = body.get("subtotalMinor");
    if (subtotal instanceof Number n) a.setSubtotalMinor(n.longValue());
    AbandonedCheckout saved = abandoned.save(a);
    // Fire abandoned_checkout automation (best-effort).
    try {
      automations.fire(sid, "abandoned_checkout", saved.getEmail(), "",
          java.util.Map.of("amount", String.valueOf(saved.getTotalMinor())));
    } catch (Exception e) { /* best-effort */ }
    return toFrontend(saved);
  }

  private Map<String, Object> toFrontend(AbandonedCheckout a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId().toString());
    m.put("email", a.getEmail());
    m.put("items", a.getCart());
    m.put("createdAt", a.getCreatedAt().toEpochSecond(ZoneOffset.UTC) * 1000);
    m.put("recovered", a.getRecoveredAt() != null);
    if (a.getRecoveredAt() != null) {
      m.put("recoveredAt", a.getRecoveredAt().toEpochSecond(ZoneOffset.UTC) * 1000);
    }
    return m;
  }
}
