package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.modules.wallet.entity.Payout;
import com.becho.saas.modules.wallet.repository.PayoutRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible payout endpoints: GET /stores/{storeId}/payouts,
 * POST /stores/{storeId}/payouts { amount }.
 * DB-backed. Frontend Payout { id, date, amount, status: "Processing"|"Paid" }.
 */
@RestController
@RequestMapping("/stores/{storeId}/payouts")
public class StorePayoutController {

  private final PayoutRepository payouts;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StorePayoutController(PayoutRepository payouts, StoreRepository stores, StoreScope scope) {
    this.payouts = payouts;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return payouts.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public Map<String, Object> request(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Object amountObj = body.get("amount");
    double amount = amountObj instanceof Number n ? n.doubleValue() : 0;
    if (amount <= 0) {
      throw ApiException.badRequest("BAD_AMOUNT", "Amount must be positive");
    }
    Payout p = new Payout();
    p.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    p.setAmountMinor(Math.round(amount * 100));
    p.setCurrency("BDT");
    p.setStatus("pending");
    LocalDate today = LocalDate.now();
    p.setPeriodStart(today.withDayOfMonth(1));
    p.setPeriodEnd(today);
    return toFrontend(payouts.save(p));
  }

  private Map<String, Object> toFrontend(Payout p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.getId().toString());
    m.put("date", p.getCreatedAt().toEpochSecond(ZoneOffset.UTC) * 1000);
    m.put("amount", p.getAmountMinor() / 100.0);
    m.put("status", "paid".equals(p.getStatus()) ? "Paid" : "Processing");
    return m;
  }
}
