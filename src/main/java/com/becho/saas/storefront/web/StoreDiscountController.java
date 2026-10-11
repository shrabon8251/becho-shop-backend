package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.discounts.entity.Discount;
import com.becho.saas.modules.discounts.repository.DiscountRepository;
import com.becho.saas.modules.discounts.service.DiscountService;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible discount endpoints: /stores/{storeId}/discounts[/{id}].
 */
@RestController
@RequestMapping("/stores/{storeId}/discounts")
public class StoreDiscountController {

  private final DiscountService discountService;
  private final DiscountRepository discounts;
  private final StoreRepository storeRepository;
  private final StoreScope scope;

  public StoreDiscountController(DiscountService discountService, DiscountRepository discounts,
                                  StoreRepository storeRepository, StoreScope scope) {
    this.discountService = discountService;
    this.discounts = discounts;
    this.storeRepository = storeRepository;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return discounts.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(sid)
        .stream().map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Discount d = new Discount();
    d.setStore(storeRepository.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    applyBody(d, body);
    return toFrontend(discountService.create(d));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Discount d;
    if (StoreScope.isNewId(id)) {
      d = new Discount();
      d.setStore(storeRepository.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    } else {
      d = discounts.findById(StoreScope.lid(id))
          .filter(x -> x.getStore() != null && sid.equals(x.getStore().getId()))
          .orElseThrow(() -> ApiException.notFound("Discount"));
    }
    applyBody(d, body);
    return toFrontend(discountService.create(d));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Discount d = discounts.findById(StoreScope.lid(id))
        .filter(x -> x.getStore() != null && sid.equals(x.getStore().getId()))
        .orElseThrow(() -> ApiException.notFound("Discount"));
    d.setDeletedAt(java.time.LocalDateTime.now());
    discounts.save(d);
  }

  private void applyBody(Discount d, Map<String, Object> body) {
    if (body.get("code") != null) d.setCode(((String) body.get("code")).trim().toUpperCase());
    if (body.get("title") != null) d.setTitle((String) body.get("title"));
    String type = (String) body.get("type");
    Object valueObj = body.get("value");
    if ("%".equals(type)) {
      d.setDiscountType("percentage");
      if (valueObj instanceof Number n) {
        java.math.BigDecimal pct = new java.math.BigDecimal(n.toString());
        if (pct.compareTo(java.math.BigDecimal.ZERO) < 0
            || pct.compareTo(new java.math.BigDecimal("100")) > 0) {
          throw ApiException.badRequest("BAD_PERCENT", "Percentage must be between 0 and 100");
        }
        d.setValuePercent(pct);
      }
    } else {
      d.setDiscountType("fixed");
      if (valueObj instanceof Number n) d.setValueMinor(n.longValue());
    }
    Object active = body.get("active");
    if (active instanceof Boolean b) d.setStatus(b ? "active" : "draft");
    Object minObj = body.get("minimum");
    if (minObj instanceof Number n) d.setMinimumSubtotalMinor(n.longValue());
    Object limitObj = body.get("usageLimit");
    if (limitObj instanceof Number n) d.setUsageLimit(n.intValue());
  }

  private Map<String, Object> toFrontend(Discount d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", d.getId().toString());
    m.put("code", d.getCode());
    m.put("title", d.getTitle());
    boolean isPercent = "percentage".equals(d.getDiscountType());
    m.put("type", isPercent ? "%" : "$");
    m.put("value", isPercent && d.getValuePercent() != null
        ? d.getValuePercent().doubleValue()
        : (d.getValueMinor() != null ? d.getValueMinor() : 0));
    m.put("active", "active".equals(d.getStatus()));
    m.put("used", d.getUsedCount());
    m.put("minimum", d.getMinimumSubtotalMinor());
    m.put("usageLimit", d.getUsageLimit());
    return m;
  }
}
