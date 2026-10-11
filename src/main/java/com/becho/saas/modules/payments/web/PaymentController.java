package com.becho.saas.modules.payments.web;

import com.becho.saas.common.ApiResponse;
import com.becho.saas.modules.orders.entity.Order;
import com.becho.saas.modules.orders.repository.OrderRepository;
import com.becho.saas.modules.orders.service.OrderService;
import com.becho.saas.modules.orders.web.OrderDtos.RecordTransactionRequest;
import com.becho.saas.modules.payments.PaymentProvider;
import com.becho.saas.modules.payments.PaymentProviderRegistry;
import com.becho.saas.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/** Payment init + gateway callbacks (IPN). */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

  private final PaymentProviderRegistry registry;
  private final OrderRepository orders;
  private final OrderService orderService;

  public PaymentController(PaymentProviderRegistry registry, OrderRepository orders,
                           OrderService orderService) {
    this.registry = registry;
    this.orders = orders;
    this.orderService = orderService;
  }

  public record InitPaymentRequest(Long orderId, String provider,
                                   String successUrl, String failUrl, String cancelUrl) {}

  @PostMapping("/init")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public ApiResponse<PaymentProvider.InitResult> init(@RequestBody InitPaymentRequest req) {
    Long storeId = TenantContext.requireStoreId();
    Order order = orders.findByIdAndStoreIdAndDeletedAtIsNull(req.orderId(), storeId)
        .orElseThrow(() -> com.becho.saas.common.ApiException.notFound("Order"));
    PaymentProvider provider = registry.get(req.provider());
    var result = provider.initPayment(new PaymentProvider.InitRequest(
        storeId, order.getId(), order.getTotalMinor(), order.getCurrency(),
        order.getEmail(), order.getPhone(), req.successUrl(), req.failUrl(), req.cancelUrl()));
    return ApiResponse.ok(result);
  }

  /**
   * Gateway IPN endpoint. Provider key in path; verification is
   * server-to-server inside each provider — browser posts are never trusted.
   * The verified amount/currency/tran-id are cross-checked against the order
   * before anything is marked paid (replay + amount-tampering protection).
   */
  @PostMapping("/callback/{providerKey}")
  public ApiResponse<String> callback(@PathVariable String providerKey,
                                      HttpServletRequest request,
                                      @RequestBody(required = false) String rawBody) {
    PaymentProvider provider = registry.get(providerKey);
    Map<String, String> params = new HashMap<>();
    request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));
    var verified = provider.verifyCallback(new PaymentProvider.CallbackPayload(params, rawBody));

    // Match to order via tran_id convention "BECHO-{orderId}-{ts}"
    Long orderId = extractOrderId(params.get("tran_id"));
    if (orderId != null && verified.success()) {
      // Re-resolve tenant from the order itself — callbacks are unauthenticated.
      Order order = orders.findById(orderId).orElse(null);
      if (order != null && matchesOrder(order, verified, params.get("tran_id"))) {
        TenantContext.set(order.getStore().getId(), null);
        try {
          orderService.recordTransaction(orderId, new RecordTransactionRequest(
              "sale", providerKey, verified.amountMinor(), "success",
              verified.gatewayTxnId(), verified.rawJson()));
        } finally {
          TenantContext.clear();
        }
      }
    }
    return ApiResponse.ok(verified.status());
  }

  /**
   * The gateway's verified values must agree with OUR order. A valid signature
   * for a different (smaller) amount or another order must never mark this paid.
   */
  private static boolean matchesOrder(Order order, PaymentProvider.VerifyResult verified,
                                      String callbackTranId) {
    if (verified.amountMinor() != order.getTotalMinor()) {
      return false;
    }
    if (verified.currency() != null
        && !verified.currency().equalsIgnoreCase(order.getCurrency())) {
      return false;
    }
    // The gateway-echoed merchant tran id must embed this order's id.
    String echoed = verified.merchantTranId();
    if (echoed != null && !echoed.equals(callbackTranId)) {
      return false;
    }
    if (!("BECHO-" + order.getId() + "-").equals(prefixOf(callbackTranId))) {
      return false;
    }
    // Don't resurrect cancelled orders via IPN.
    return order.getCancelledAt() == null;
  }

  private static String prefixOf(String tranId) {
    if (tranId == null) return "";
    int last = tranId.lastIndexOf('-');
    return last < 0 ? tranId : tranId.substring(0, last + 1);
  }

  private static Long extractOrderId(String tranId) {
    if (tranId == null || !tranId.startsWith("BECHO-")) return null;
    try {
      return Long.parseLong(tranId.split("-")[1]);
    } catch (Exception e) {
      return null;
    }
  }
}
