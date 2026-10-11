package com.becho.saas.storefront.web;

import com.becho.saas.modules.orders.service.OrderService;
import com.becho.saas.modules.orders.web.OrderDtos.CreateOrderRequest;
import com.becho.saas.modules.orders.web.OrderDtos.LineInput;
import com.becho.saas.modules.orders.web.OrderDtos.OrderDto;
import com.becho.saas.modules.orders.web.OrderDtos.RecordTransactionRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible POS endpoints: POST /stores/{storeId}/pos/sales.
 * A POS sale is an in-person order: stock is decremented server-side via
 * OrderService.create with source="pos".
 */
@RestController
@RequestMapping("/stores/{storeId}/pos")
public class StorePosController {

    private final OrderService orderService;
    private final StoreScope scope;

    public StorePosController(OrderService orderService, StoreScope scope) {
        this.orderService = orderService;
        this.scope = scope;
    }

    @PostMapping("/sales")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> sale(@PathVariable String storeId,
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

        Object locObj = body.get("locationId");
        Long locationId = locObj == null ? null
                : (locObj instanceof Number n ? n.longValue() : StoreScope.lid(String.valueOf(locObj)));

        // Frontend POS sends {customerId, items, code, method}; accept the legacy
        // names (payMethod/discountCode) too.
        Object custObj = body.get("customerId");
        Long customerId = custObj == null ? null
                : (custObj instanceof Number n ? n.longValue() : StoreScope.lid(String.valueOf(custObj)));
        String payMethod = firstNonBlank((String) body.get("payMethod"), (String) body.get("method"), "cash");
        String discountCode = firstNonBlank((String) body.get("discountCode"), (String) body.get("code"), null);

        var req = new CreateOrderRequest(
                customerId,
                (String) body.get("email"),
                (String) body.get("phone"),
                lines,
                locationId,
                payMethod,
                null,
                null,
                null,
                (String) body.get("notes"),
                "pos",
                discountCode,
                null,
                null);
        OrderDto dto = orderService.create(req);
        // A POS sale is complete at the till: record it paid and convert the
        // stock reservation into a sale. Without this the order stayed "pending"
        // with stock reserved forever and on-hand never decremented.
        dto = orderService.recordTransaction(dto.id(), new RecordTransactionRequest(
                "sale", payMethod, dto.totalMinor(), "success", null, null));
        dto = orderService.fulfill(dto.id());
        Map<String, Object> m = toFrontend(dto);
        m.put("channel", "pos");
        return m;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private Map<String, Object> toFrontend(OrderDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id());
        m.put("code", "#" + d.orderNumber());
        m.put("pay", d.financialStatus());
        m.put("ful", d.fulfillmentStatus());
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
}
