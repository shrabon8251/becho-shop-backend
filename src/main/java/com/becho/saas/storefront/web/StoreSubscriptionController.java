package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.subscriptions.entity.Subscription;
import com.becho.saas.modules.subscriptions.repository.SubscriptionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible subscription endpoints: /stores/{storeId}/subscriptions[/{id}].
 * Uses the real Subscription entity (customer_subscriptions table).
 */
@RestController
@RequestMapping("/stores/{storeId}/subscriptions")
public class StoreSubscriptionController {

  private final SubscriptionRepository subscriptions;
  private final StoreScope scope;

  public StoreSubscriptionController(SubscriptionRepository subscriptions, StoreScope scope) {
    this.subscriptions = subscriptions;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return subscriptions.findAll().stream()
        .filter(s -> s.getStore() != null && sid.equals(s.getStore().getId()))
        .map(this::toFrontend)
        .collect(Collectors.toList());
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> setStatus(@PathVariable String storeId,
                                        @PathVariable String id,
                                        @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Subscription s = subscriptions.findById(StoreScope.lid(id))
        .orElseThrow(() -> ApiException.notFound("Subscription"));
    if (s.getStore() == null || !sid.equals(s.getStore().getId())) {
      throw ApiException.forbidden();
    }
    if (body.get("status") != null) {
      s.setStatus(((String) body.get("status")).toLowerCase());
      if ("cancelled".equalsIgnoreCase((String) body.get("status"))) {
        s.setCancelledAt(java.time.LocalDateTime.now());
      }
      subscriptions.save(s);
    }
    return toFrontend(s);
  }

  private Map<String, Object> toFrontend(Subscription s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId().toString());
    m.put("customerId", s.getCustomer() != null ? s.getCustomer().getId().toString() : null);
    m.put("planName", s.getPlanName());
    m.put("interval", s.getInterval());
    m.put("price", s.getPriceMinor());
    m.put("currency", s.getCurrency());
    m.put("status", s.getStatus());
    m.put("nextBillingAt", s.getNextBillingAt() != null
        ? s.getNextBillingAt().toInstant(ZoneOffset.UTC).toEpochMilli() : null);
    return m;
  }
}
