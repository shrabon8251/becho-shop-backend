package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.orders.entity.Order;
import com.becho.saas.modules.orders.entity.OrderReturn;
import com.becho.saas.modules.orders.repository.OrderRepository;
import com.becho.saas.modules.orders.repository.OrderReturnRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Return endpoints: GET/POST /stores/{storeId}/returns,
 * PATCH /stores/{storeId}/returns/{id} (status change).
 * Frontend OrderReturn: { id, orderId, items, status:
 * "Requested"|"Approved"|"Completed"|"Declined", refundAmount, createdAt }.
 */
@RestController
@RequestMapping("/stores/{storeId}/returns")
public class StoreReturnController {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  /** Frontend status -> DB status. */
  private static final Map<String, String> TO_DB = Map.of(
      "Requested", "requested",
      "Approved", "approved",
      "Completed", "completed",
      "Declined", "rejected");
  private static final Set<String> FROM_DB = Set.of("requested", "approved", "completed", "rejected");

  private final OrderReturnRepository returns;
  private final OrderRepository orders;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreReturnController(OrderReturnRepository returns, OrderRepository orders,
                                StoreRepository stores, StoreScope scope) {
    this.returns = returns;
    this.orders = orders;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return returns.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    Object rawOrderId = body.get("orderId");
    if (rawOrderId == null) {
      throw ApiException.badRequest("BAD_ORDER", "orderId is required");
    }
    Long orderId;
    try {
      orderId = Long.valueOf(String.valueOf(rawOrderId));
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("BAD_ORDER", "Invalid orderId");
    }
    Order order = orders.findByIdAndStoreIdAndDeletedAtIsNull(orderId, sid)
        .orElseThrow(() -> ApiException.notFound("Order"));
    OrderReturn r = new OrderReturn();
    r.setStore(store);
    r.setOrder(order);
    r.setReason(body.get("reason") != null
        ? String.valueOf(body.get("reason")) : "Customer requested return");
    if (body.get("items") != null) r.setItems(toJson(body.get("items")));
    return toFrontend(returns.save(r));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> setStatus(@PathVariable String storeId,
                                       @PathVariable String id,
                                       @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    OrderReturn r = returns.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Return"));
    if (body.get("status") != null) {
      String frontendStatus = String.valueOf(body.get("status"));
      String dbStatus = TO_DB.get(frontendStatus);
      if (dbStatus == null || !FROM_DB.contains(dbStatus)) {
        throw ApiException.badRequest("BAD_STATUS", "Unknown status: " + frontendStatus);
      }
      r.setStatus(dbStatus);
    }
    return toFrontend(returns.save(r));
  }

  private String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception e) {
      return "[]";
    }
  }

  private Map<String, Object> toFrontend(OrderReturn r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId().toString());
    m.put("orderId", r.getOrder().getId());
    m.put("items", fromJson(r.getItems()));
    m.put("status", switch (r.getStatus()) {
      case "requested" -> "Requested";
      case "approved" -> "Approved";
      case "completed" -> "Completed";
      default -> "Declined";
    });
    m.put("refundAmount", 0);
    m.put("createdAt", r.getCreatedAt().atZone(ZoneOffset.UTC).toInstant().toEpochMilli());
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
