package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.orders.entity.DraftOrder;
import com.becho.saas.modules.orders.repository.DraftOrderRepository;
import com.becho.saas.modules.orders.service.OrderService;
import com.becho.saas.modules.orders.web.OrderDtos;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draft order endpoints: GET/POST /stores/{storeId}/draft-orders,
 * PUT/DELETE /stores/{storeId}/draft-orders/{id},
 * POST /stores/{storeId}/draft-orders/{id}/complete.
 * Frontend DraftOrder: { id, customerId?, items, note?, code?, zoneId?,
 *                        status: "Open"|"Completed", createdAt }.
 */
@RestController
@RequestMapping("/stores/{storeId}/draft-orders")
public class StoreDraftOrderController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final DraftOrderRepository drafts;
  private final StoreRepository stores;
  private final StoreScope scope;
  private final OrderService orderService;

  public StoreDraftOrderController(DraftOrderRepository drafts,
                                    StoreRepository stores, StoreScope scope,
                                    OrderService orderService) {
    this.drafts = drafts;
    this.stores = stores;
    this.scope = scope;
    this.orderService = orderService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return drafts.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    DraftOrder d = new DraftOrder();
    d.setStore(store);
    applyBody(d, body);
    return toFrontend(drafts.save(d));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    DraftOrder d;
    if (StoreScope.isNewId(id)) {
      Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
      d = new DraftOrder();
      d.setStore(store);
    } else {
      d = drafts.findByIdAndStoreId(StoreScope.lid(id), sid)
          .orElseThrow(() -> ApiException.notFound("DraftOrder"));
    }
    applyBody(d, body);
    return toFrontend(drafts.save(d));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    DraftOrder d = drafts.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("DraftOrder"));
    drafts.delete(d);
  }

  @PostMapping("/{id}/complete")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> complete(@PathVariable String storeId,
                                      @PathVariable String id) {
    Long sid = scope.require(storeId);
    DraftOrder d = drafts.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("DraftOrder"));
    if ("completed".equals(d.getStatus())) {
      throw ApiException.badRequest("ALREADY_COMPLETED", "Draft order already completed");
    }
    // Ensure tenant context for OrderService (filter sets it, but be explicit).
    com.becho.saas.tenancy.TenantContext.set(sid, null);
    try {
      // Parse draft lines [{variantId, qty}] → LineInput list.
      List<OrderDtos.LineInput> lines = parseLines(d.getLines());
      if (lines.isEmpty()) {
        throw ApiException.badRequest("NO_LINES", "Draft order has no items");
      }
      // Create the real order (handles stock reservation, pricing, etc.).
      var orderReq = new OrderDtos.CreateOrderRequest(
          d.getCustomerId(),
          d.getEmail(),
          null, // phone
          lines,
          null, // locationId (resolved to store default)
          "cod", // draft orders are merchant-created; COD default
          null, // shippingRateId
          null, // shippingAddress
          null, // billingAddress
          null, // notes
          "draft",
          null, // discountCode
          null, // giftCode
          null  // loyaltyPoints
      );
      var order = orderService.create(orderReq);
      // Mark draft completed.
      d.setStatus("completed");
      Map<String, Object> m = toFrontend(drafts.save(d));
      m.put("orderId", order.id().toString());
      m.put("orderNumber", order.orderNumber());
      m.put("pay", "Pending");
      m.put("ful", "Unfulfilled");
      return m;
    } finally {
      com.becho.saas.tenancy.TenantContext.clear();
    }
  }

  /** Parses draft lines JSON [{variantId, qty}] into LineInput list. */
  private List<OrderDtos.LineInput> parseLines(String json) {
    List<OrderDtos.LineInput> out = new ArrayList<>();
    if (json == null || json.isBlank()) return out;
    try {
      List<Map<String, Object>> items = MAPPER.readValue(json,
          MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
      for (Map<String, Object> item : items) {
        Object vid = item.get("variantId");
        Object qty = item.get("qty");
        if (vid == null || qty == null) continue;
        try {
          Long variantId = Long.valueOf(String.valueOf(vid));
          int quantity = ((Number) qty).intValue();
          if (quantity > 0) out.add(new OrderDtos.LineInput(variantId, quantity));
        } catch (NumberFormatException ignored) { /* skip bad line */ }
      }
    } catch (Exception e) {
      // malformed JSON → empty list → NO_LINES error above
    }
    return out;
  }

  private void applyBody(DraftOrder d, Map<String, Object> body) {
    if (body.get("customerId") != null) {
      try {
        d.setCustomerId(Long.valueOf(String.valueOf(body.get("customerId"))));
      } catch (NumberFormatException ignored) { /* keep null */ }
    }
    if (body.get("items") != null) d.setLines(toJson(body.get("items")));
    if (body.get("email") != null) d.setEmail(String.valueOf(body.get("email")));
    Object status = body.get("status");
    if (status != null) {
      String s = String.valueOf(status);
      d.setStatus("Completed".equalsIgnoreCase(s) ? "completed" : "draft");
    }
  }

  private String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception e) {
      return "[]";
    }
  }

  private Map<String, Object> toFrontend(DraftOrder d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", d.getId().toString());
    if (d.getCustomerId() != null) m.put("customerId", d.getCustomerId().toString());
    m.put("items", fromJson(d.getLines()));
    m.put("status", "completed".equals(d.getStatus()) ? "Completed" : "Open");
    m.put("createdAt", d.getCreatedAt().atZone(ZoneOffset.UTC).toInstant().toEpochMilli());
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
