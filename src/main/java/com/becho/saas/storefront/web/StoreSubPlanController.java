package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.catalog.entity.ProductVariant;
import com.becho.saas.modules.catalog.repository.ProductVariantRepository;
import com.becho.saas.modules.subscriptions.entity.SubscriptionPlan;
import com.becho.saas.modules.subscriptions.repository.SubscriptionPlanRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible subscription plan endpoints: /stores/{storeId}/subscription-plans[/{id}].
 * DB-backed. Frontend SubPlan { id, productId, interval, discountPct, active }.
 * The discount is materialized into price_minor at creation/update from the
 * variant's current price.
 */
@RestController
@RequestMapping("/stores/{storeId}/subscription-plans")
public class StoreSubPlanController {

  private final SubscriptionPlanRepository plans;
  private final ProductVariantRepository variants;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreSubPlanController(SubscriptionPlanRepository plans,
                                 ProductVariantRepository variants,
                                 StoreRepository stores, StoreScope scope) {
    this.plans = plans;
    this.variants = variants;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return plans.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    SubscriptionPlan p = new SubscriptionPlan();
    p.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    applyBody(sid, p, body);
    return toFrontend(plans.save(p));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    SubscriptionPlan p;
    if (StoreScope.isNewId(id)) {
      p = new SubscriptionPlan();
      p.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    } else {
      p = plans.findByIdAndStoreId(StoreScope.lid(id), sid)
          .orElseThrow(() -> ApiException.notFound("SubscriptionPlan"));
    }
    applyBody(sid, p, body);
    return toFrontend(plans.save(p));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    SubscriptionPlan p = plans.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("SubscriptionPlan"));
    plans.delete(p);
  }

  private void applyBody(Long sid, SubscriptionPlan p, Map<String, Object> body) {
    if (body.get("name") != null) p.setName(String.valueOf(body.get("name")));
    else if (p.getName() == null) p.setName("Subscription plan");
    if (body.get("interval") != null) {
      String iv = String.valueOf(body.get("interval")).toLowerCase();
      p.setInterval(switch (iv) {
        case "weekly", "monthly", "quarterly", "yearly" -> iv;
        default -> "monthly";
      });
    }
    if (body.get("active") instanceof Boolean b) {
      p.setStatus(b ? "active" : "paused");
    }
    // Resolve product -> first variant; discount materialized into price_minor.
    Object pidObj = body.get("productId");
    if (pidObj != null) {
      Long productId = StoreScope.lid(String.valueOf(pidObj));
      if (productId != null) {
        List<ProductVariant> vs = variants.findByProductIdAndDeletedAtIsNullOrderByPosition(productId);
        ProductVariant v = vs.stream()
            .filter(x -> x.getStore() != null && sid.equals(x.getStore().getId()))
            .findFirst().orElse(null);
        if (v != null) {
          p.setVariant(v);
          double discountPct = body.get("discountPct") instanceof Number n ? n.doubleValue() : 0;
          discountPct = Math.min(90, Math.max(0, discountPct));
          p.setPriceMinor(Math.round(v.getPriceMinor() * (1 - discountPct / 100.0)));
        }
      }
    }
  }

  private Map<String, Object> toFrontend(SubscriptionPlan p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.getId().toString());
    if (p.getVariant() != null && p.getVariant().getProduct() != null) {
      m.put("productId", p.getVariant().getProduct().getId().toString());
    }
    m.put("name", p.getName());
    m.put("interval", p.getInterval());
    // Back out the discount pct from variant price vs plan price.
    double discountPct = 0;
    if (p.getVariant() != null && p.getVariant().getPriceMinor() > 0) {
      discountPct = Math.round((1 - (double) p.getPriceMinor() / p.getVariant().getPriceMinor()) * 100);
    }
    m.put("discountPct", discountPct);
    m.put("price", p.getPriceMinor() / 100.0);
    m.put("active", "active".equals(p.getStatus()));
    return m;
  }
}
