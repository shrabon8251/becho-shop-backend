package com.becho.saas.storefront.web;

import com.becho.saas.modules.orders.entity.Order;
import com.becho.saas.modules.orders.repository.OrderRepository;
import com.becho.saas.modules.orders.service.OrderService;
import com.becho.saas.modules.orders.web.OrderDtos.CreateOrderRequest;
import com.becho.saas.modules.orders.web.OrderDtos.LineInput;
import com.becho.saas.modules.orders.web.OrderDtos.OrderDto;
import com.becho.saas.modules.orders.web.OrderDtos.RecordTransactionRequest;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible order endpoints: /stores/{storeId}/orders[/{id}][/cancel|/refund].
 */
@RestController
@RequestMapping("/stores/{storeId}/orders")
public class StoreOrderController {

    private final OrderService orderService;
    private final OrderRepository orders;
    private final StoreScope scope;

    public StoreOrderController(OrderService orderService, OrderRepository orders, StoreScope scope) {
        this.orderService = orderService;
        this.orders = orders;
        this.scope = scope;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public List<Map<String, Object>> list(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        return orders.findPage(sid, null, null, null, Limit.of(100))
                .stream().map(this::toFrontend).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public Map<String, Object> get(@PathVariable String storeId, @PathVariable Long id) {
        scope.require(storeId);
        return toFrontend(orderService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> create(@PathVariable String storeId,
                                      @RequestBody Map<String, Object> body) {
        scope.require(storeId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        List<LineInput> lines = items == null ? List.of() : items.stream().map(it -> {
            Object vid = it.get("variantId");
            Long variantId = vid instanceof Number n ? n.longValue() : StoreScope.lid(String.valueOf(vid));
            int qty = it.get("qty") instanceof Number n ? n.intValue() : 1;
            return new LineInput(variantId, qty);
        }).collect(Collectors.toList());
        var req = new CreateOrderRequest(
                null,
                (String) body.get("email"),
                (String) body.get("phone"),
                lines,
                null,
                (String) body.getOrDefault("payMethod", "cod"),
                null,
                (String) body.get("shippingAddress"),
                (String) body.get("billingAddress"),
                (String) body.get("notes"),
                "storefront",
                (String) body.get("discountCode"),
                (String) body.get("giftCode"),
                null);
        return toFrontend(orderService.create(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> update(@PathVariable String storeId,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        scope.require(storeId);
        @SuppressWarnings("unchecked")
        Map<String, Object> patch = (Map<String, Object>) body.get("patch");
        OrderDto dto = orderService.get(id);
        if (patch != null && "Paid".equals(patch.get("pay"))) {
            dto = orderService.recordTransaction(id, new RecordTransactionRequest(
                    "sale", "manual", dto.totalMinor(), "success", null, null));
        }
        Map<String, Object> m = toFrontend(dto);
        if (patch != null && patch.get("ful") != null) m.put("ful", patch.get("ful"));
        return m;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> cancel(@PathVariable String storeId, @PathVariable Long id) {
        scope.require(storeId);
        return toFrontend(orderService.cancel(id));
    }

    @PostMapping("/{id}/fulfill")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> fulfill(@PathVariable String storeId,
                                       @PathVariable Long id) {
        scope.require(storeId);
        return toFrontend(orderService.fulfill(id));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> refund(@PathVariable String storeId,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        scope.require(storeId);
        long amountMinor = body.get("amount") instanceof Number n ? n.longValue() : 0;
        OrderDto dto = orderService.recordTransaction(id, new RecordTransactionRequest(
                "refund", "manual", amountMinor, "success", null, null));
        Map<String, Object> m = toFrontend(dto);
        m.put("refunded", amountMinor);
        m.put("pay", "Refunded");
        return m;
    }

    private Map<String, Object> toFrontend(OrderDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id());
        m.put("code", "#" + d.orderNumber());
        m.put("pay", mapPay(d.financialStatus()));
        m.put("ful", mapFul(d.fulfillmentStatus()));
        m.put("createdAt", d.createdAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        m.put("email", d.email());
        m.put("phone", d.phone());
        m.put("subtotal", d.subtotalMinor());
        m.put("discount", d.discountMinor());
        m.put("shipping", d.shippingMinor());
        m.put("tax", d.taxMinor());
        m.put("total", d.totalMinor());
        m.put("currency", d.currency());
        m.put("payMethod", d.paymentMethod());
        m.put("items", d.lines().stream().map(l -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("variantId", l.variantId() != null ? l.variantId().toString() : "");
            im.put("qty", l.quantity());
            im.put("title", l.title());
            im.put("price", l.priceMinor());
            return im;
        }).collect(Collectors.toList()));
        m.put("log", List.of());
        m.put("refunded", 0);
        return m;
    }

    private Map<String, Object> toFrontend(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("code", "#" + o.getOrderNumber());
        m.put("pay", mapPay(o.getFinancialStatus()));
        m.put("ful", mapFul(o.getFulfillmentStatus()));
        m.put("createdAt", o.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        m.put("email", o.getEmail());
        m.put("phone", o.getPhone());
        m.put("total", o.getTotalMinor());
        m.put("currency", o.getCurrency());
        m.put("payMethod", o.getPaymentMethod());
        m.put("items", List.of());
        m.put("log", List.of());
        m.put("refunded", 0);
        return m;
    }

    private String mapPay(String s) {
        if (s == null) return "Pending";
        return switch (s.toLowerCase()) {
            case "paid" -> "Paid";
            case "refunded", "partially_refunded" -> "Refunded";
            case "voided", "cancelled" -> "Cancelled";
            default -> "Pending";
        };
    }

    private String mapFul(String s) {
        if (s == null) return "Unfulfilled";
        return switch (s.toLowerCase()) {
            case "fulfilled" -> "Fulfilled";
            case "cancelled" -> "Cancelled";
            default -> "Unfulfilled";
        };
    }
}
