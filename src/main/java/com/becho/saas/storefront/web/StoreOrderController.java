package com.becho.saas.storefront.web;

import com.becho.saas.modules.orders.entity.Order;
import com.becho.saas.modules.orders.repository.OrderRepository;
import com.becho.saas.modules.orders.service.OrderService;
import com.becho.saas.modules.orders.web.OrderDtos.CreateOrderRequest;
import com.becho.saas.modules.orders.web.OrderDtos.LineInput;
import com.becho.saas.modules.orders.web.OrderDtos.OrderDto;
import com.becho.saas.modules.orders.web.OrderDtos.RecordTransactionRequest;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

    /**
     * Admin Orders page — one server-side page (default 20 rows). Filters + sort run
     * on the backend; the old list endpoint above is untouched (Dashboard etc. use it).
     */
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public Map<String, Object> page(@PathVariable String storeId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String tab,
                                    @RequestParam(required = false) String pay,
                                    @RequestParam(required = false) String ful,
                                    @RequestParam(required = false) Integer range,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "newest") String sort) {
        Long sid = scope.require(storeId);
        size = Math.min(Math.max(size, 1), 100);
        page = Math.max(page, 0);
        LocalDateTime since = range != null && range > 0 ? LocalDateTime.now().minusDays(range) : null;
        String tabV = tab != null && !tab.isBlank() && !"all".equalsIgnoreCase(tab) ? tab.toLowerCase() : null;
        String payV = normalizeStatus(pay);
        String fulV = normalizeStatus(ful);
        String qV = q != null && !q.isBlank() ? q.trim() : null;
        Long qNum = null;
        if (qV != null && qV.matches("\\d+")) {
            try { qNum = Long.parseLong(qV); } catch (NumberFormatException ignored) { /* not a number after all */ }
        }
        // Sort is an allowlist switch — no client string ever reaches the query.
        Sort sortObj = switch (sort) {
            case "oldest" -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case "total-desc" -> Sort.by(Sort.Order.desc("totalMinor"));
            case "total-asc" -> Sort.by(Sort.Order.asc("totalMinor"));
            default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
        Page<Order> result = orders.findAdminPage(sid, tabV, payV, fulV, since, qV, qNum, PageRequest.of(page, size, sortObj));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", result.getContent().stream().map(this::toFrontend).collect(Collectors.toList()));
        m.put("total", result.getTotalElements());
        m.put("hasMore", result.hasNext());
        return m;
    }

    /** Exact summary counts for the Orders stat strip (range in days, null = all time). */
    @GetMapping("/counts")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public Map<String, Object> counts(@PathVariable String storeId,
                                      @RequestParam(required = false) Integer range) {
        Long sid = scope.require(storeId);
        LocalDateTime since = range != null && range > 0 ? LocalDateTime.now().minusDays(range) : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", orders.countTotal(sid, since));
        m.put("unfulfilled", orders.countUnfulfilled(sid, since));
        m.put("unpaid", orders.countUnpaid(sid, since));
        m.put("totalSalesMinor", orders.sumPaidTotal(sid, since));
        return m;
    }

    private static String normalizeStatus(String v) {
        return v != null && !v.isBlank() ? v.trim().toLowerCase() : null;
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
        // Manual orders (admin Orders page) send customerId + payMethod/paymentMethod +
        // discountCode/code + notes/note; the storefront checkout shape is accepted too.
        Object cid = body.get("customerId");
        Long customerId = cid instanceof Number n ? n.longValue()
                : cid != null && !String.valueOf(cid).isBlank() ? StoreScope.lid(String.valueOf(cid)) : null;
        var req = new CreateOrderRequest(
                customerId,
                (String) body.get("email"),
                (String) body.get("phone"),
                lines,
                null,
                firstStr(body, "cod", "payMethod", "paymentMethod"),
                null,
                (String) body.get("shippingAddress"),
                (String) body.get("billingAddress"),
                firstStr(body, null, "notes", "note"),
                "manual",
                firstStr(body, null, "discountCode", "code"),
                (String) body.get("giftCode"),
                null);
        return toFrontend(orderService.create(req));
    }

    /** First non-blank String among the given keys, or the fallback when none is set. */
    private static String firstStr(Map<String, Object> body, String fallback, String... keys) {
        for (String k : keys) {
            Object v = body.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return fallback;
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
        // The list used to send no customerId, so the frontend showed "-" for every
        // customer. Walk-in orders have no customer row.
        m.put("customerId", o.getCustomer() != null ? o.getCustomer().getId().toString() : "walkin");
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
