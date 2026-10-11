package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.orders.entity.Order;
import com.becho.saas.modules.orders.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public order tracking: POST /stores/{storeId}/track {orderId, email}.
 * Returns only non-sensitive fields. Wrong ID and wrong email produce the
 * same error so order existence cannot be enumerated.
 */
@RestController
@RequestMapping("/stores/{storeId}/track")
public class StoreTrackingController {

  private final OrderRepository orders;

  public StoreTrackingController(OrderRepository orders) {
    this.orders = orders;
  }

  @PostMapping
  @Transactional(readOnly = true)
  public Map<String, Object> lookup(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    Long sid = StoreScope.lid(storeId);
    Object orderIdObj = body.get("orderId");
    Long orderId = orderIdObj instanceof Number n ? n.longValue()
        : StoreScope.lid(String.valueOf(orderIdObj));
    String email = body.get("email") instanceof String s ? s.trim().toLowerCase() : "";

    Order order = orders.findById(orderId).orElse(null);
    boolean matches = order != null
        && order.getStore() != null && sid.equals(order.getStore().getId())
        && order.getEmail() != null && order.getEmail().equalsIgnoreCase(email);
    if (!matches) {
      // Same error for wrong ID or wrong email — no enumeration.
      throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
          "No order found with those details");
    }

    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", order.getId());
    m.put("code", "#" + order.getOrderNumber());
    // Frontend-compatible shape (TrackedOrder): {id, pay, ful, createdAt, log, items}.
    // Backend stores lowercase statuses; the frontend expects capitalized PayStatus/FulfillStatus.
    m.put("pay", toPayStatus(order.getFinancialStatus()));
    m.put("ful", toFulfillStatus(order.getFulfillmentStatus()));
    m.put("createdAt", order.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
    m.put("log", buildTimeline(order));
    m.put("total", order.getTotalMinor());
    m.put("currency", order.getCurrency());
    m.put("items", order.getLines() == null ? List.of() : order.getLines().stream().map(l -> {
      Map<String, Object> im = new LinkedHashMap<>();
      im.put("title", l.getTitle());
      im.put("variant", l.getSku() != null ? l.getSku() : "");
      im.put("qty", l.getQuantity());
      return im;
    }).collect(Collectors.toList()));
    // Tracking info is non-sensitive; courier details omitted when absent.
    return m;
  }

  /** "paid" -> "Paid", "pending" -> "Pending", "refunded" -> "Refunded", else "Cancelled". */
  private String toPayStatus(String s) {
    if (s == null) return "Pending";
    return switch (s.trim().toLowerCase()) {
      case "paid" -> "Paid";
      case "refunded", "partially_refunded" -> "Refunded";
      case "voided", "cancelled" -> "Cancelled";
      default -> "Pending";
    };
  }

  /** "fulfilled" -> "Fulfilled", "cancelled" -> "Cancelled", else "Unfulfilled". */
  private String toFulfillStatus(String s) {
    if (s == null) return "Unfulfilled";
    return switch (s.trim().toLowerCase()) {
      case "fulfilled", "shipped", "delivered" -> "Fulfilled";
      case "cancelled" -> "Cancelled";
      default -> "Unfulfilled";
    };
  }

  /** Build a simple timeline from the order's lifecycle timestamps. */
  private List<Map<String, Object>> buildTimeline(Order order) {
    List<Map<String, Object>> log = new java.util.ArrayList<>();
    long created = order.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    log.add(Map.of("text", "Order placed", "at", created));
    if ("paid".equalsIgnoreCase(order.getFinancialStatus()) && order.getProcessedAt() != null) {
      log.add(Map.of("text", "Payment confirmed",
          "at", order.getProcessedAt().toInstant(ZoneOffset.UTC).toEpochMilli()));
    }
    if (order.getCancelledAt() != null) {
      log.add(Map.of("text", "Order cancelled",
          "at", order.getCancelledAt().toInstant(ZoneOffset.UTC).toEpochMilli()));
    }
    return log;
  }
}
