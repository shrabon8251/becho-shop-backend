package com.becho.saas.storefront.web;

import com.becho.saas.modules.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Platform billing for a store:
 * <ul>
 *   <li>{@code GET /stores/{storeId}/billing/plans} — plans + cycles + server prices
 *       + the platform's merchant MFS numbers (for manual plan payments)</li>
 *   <li>{@code POST /stores/{storeId}/billing/quote} — server-side price breakdown
 *       ({plan, cycle, promoCode?}); the promo is validated but NOT redeemed</li>
 *   <li>{@code POST /stores/{storeId}/billing/subscribe} — validates + atomically
 *       redeems the promo, records the plan on the store</li>
 * </ul>
 * All money math happens here. The frontend must never send a total.
 */
@RestController
@RequestMapping("/stores/{storeId}/billing")
public class StoreBillingController {

    private final BillingService billing;
    private final StoreScope scope;

    // BUG-42 fix: the platform's merchant MFS numbers (where merchants send plan
    // payments) were hardcoded as "01XXXXXXXXX" in the frontend, so no plan could
    // ever be paid for. They now come from config — set MERCHANT_BKASH /
    // MERCHANT_NAGAD / MERCHANT_ROCKET env vars on the deploy (Render dashboard →
    // Environment). Empty = not configured (frontend shows a notice instead of a
    // fake number).
    @Value("${becho.billing.merchant.bkash:}")
    private String merchantBkash;
    @Value("${becho.billing.merchant.nagad:}")
    private String merchantNagad;
    @Value("${becho.billing.merchant.rocket:}")
    private String merchantRocket;

    public StoreBillingController(BillingService billing, StoreScope scope) {
        this.billing = billing;
        this.scope = scope;
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public Map<String, Object> plans(@PathVariable String storeId) {
        scope.require(storeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("currency", "BDT");
        m.put("plans", BillingService.PLAN_PRICES.entrySet().stream()
                .map(e -> Map.of("name", e.getKey(), "monthlyPrice", e.getValue()))
                .collect(Collectors.toList()));
        m.put("cycles", BillingService.CYCLES.stream()
                .map(c -> Map.of("id", c.id(), "months", c.months(),
                        "off", c.off().doubleValue()))
                .collect(Collectors.toList()));
        // BUG-42 fix: expose the platform merchant numbers for manual plan payments.
        Map<String, String> numbers = new LinkedHashMap<>();
        numbers.put("bkash", merchantBkash == null ? "" : merchantBkash.trim());
        numbers.put("nagad", merchantNagad == null ? "" : merchantNagad.trim());
        numbers.put("rocket", merchantRocket == null ? "" : merchantRocket.trim());
        m.put("merchantNumbers", numbers);
        return m;
    }

    public record QuoteRequest(String plan, String cycle, String promoCode) {}

    @PostMapping("/quote")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public Map<String, Object> quote(@PathVariable String storeId,
                                     @RequestBody QuoteRequest req) {
        scope.require(storeId);
        return billing.quote(req.plan(), req.cycle(), req.promoCode());
    }

    public record SubscribeRequest(String plan, String cycle, String promoCode) {}

    @PostMapping("/subscribe")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> subscribe(@PathVariable String storeId,
                                         @RequestBody SubscribeRequest req) {
        Long sid = scope.require(storeId);
        return billing.subscribe(sid, req.plan(), req.cycle(), req.promoCode());
    }

    public record BillingMethodRequest(String kind, String label, String trxId) {}

    @GetMapping("/method")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> getMethod(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        return billing.getBillingMethod(sid);
    }

    @PutMapping("/method")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> saveMethod(@PathVariable String storeId,
                                          @RequestBody BillingMethodRequest req) {
        Long sid = scope.require(storeId);
        return billing.saveBillingMethod(sid, req.kind(), req.label(), req.trxId());
    }

    public record ConfirmRequest(String trxId) {}

    /**
     * Confirm a pending subscription payment (manual TrxID flow). The merchant
     * pays via their saved method, enters the TrxID, and the plan activates.
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> confirm(@PathVariable String storeId,
                                       @RequestBody(required = false) ConfirmRequest req) {
        Long sid = scope.require(storeId);
        return billing.confirmSubscriptionPayment(sid, req == null ? null : req.trxId());
    }
}
