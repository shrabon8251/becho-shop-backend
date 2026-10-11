package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.orders.service.OrderService;
import com.becho.saas.modules.orders.web.OrderDtos.CreateOrderRequest;
import com.becho.saas.modules.orders.web.OrderDtos.LineInput;
import com.becho.saas.modules.orders.web.OrderDtos.OrderDto;
import com.becho.saas.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public storefront checkout: creates a pending order from cart data.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Rate-limited by {@code RateLimitFilter} (public endpoint bucket).</li>
 *   <li>All money comes from the database — prices, shipping rates and
 *       discounts are resolved server-side; the client only sends ids.</li>
 *   <li>Gateway orders (sslcommerz) are created <em>pending</em> — they are
 *       only marked paid after the gateway callback is verified
 *       server-to-server. See {@code PaymentController}.</li>
 *   <li>Manual MFS orders carry the customer's TrxID and stay
 *       "awaiting verification" until the merchant confirms.</li>
 * </ul>
 */
@RestController
@RequestMapping("/stores/{storeId}/checkout")
public class StoreCheckoutController {

  private static final Set<String> ALLOWED_METHODS = Set.of(
      "cod", "sslcommerz", "manual-bkash", "manual-nagad", "manual-rocket");

  private final OrderService orderService;
  private final StoreScope scope;

  public StoreCheckoutController(OrderService orderService, StoreScope scope) {
    this.orderService = orderService;
    this.scope = scope;
  }

  public record CustomerInput(
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Email @Size(max = 255) String email,
      @Size(max = 32) String phone) {}

  public record ItemInput(
      @NotNull Long variantId,
      @Min(1) @Max(999) int qty) {}

  public record CheckoutRequest(
      @Valid @NotNull CustomerInput customer,
      @Valid @NotNull @Size(min = 1, max = 50) List<ItemInput> items,
      @NotBlank String paymentMethod,
      String zoneId,
      String rateId,
      @Size(max = 64) String code,
      @Size(max = 64) String giftCode,
      @Size(max = 500) String note,
      @Size(max = 64) String trxId,
      Long customerId,
      Integer loyaltyPoints) {}

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> checkout(@PathVariable String storeId,
                                      @Valid @RequestBody CheckoutRequest req) {
    Long sid = StoreScope.lid(storeId);
    String method = req.paymentMethod().trim().toLowerCase();
    if (!ALLOWED_METHODS.contains(method)) {
      throw ApiException.badRequest("BAD_PAYMENT_METHOD", "Unknown payment method");
    }
    // Manual methods require the customer's TrxID; gateway methods must NOT
    // carry a client-supplied transaction id (it comes from the gateway).
    if (method.startsWith("manual-")
        && (req.trxId() == null || req.trxId().isBlank())) {
      throw ApiException.badRequest("TRXID_REQUIRED",
          "Please enter the TrxID from your manual payment");
    }
    if (method.equals("sslcommerz") && req.trxId() != null && !req.trxId().isBlank()) {
      throw ApiException.badRequest("BAD_TRXID",
          "Transaction id comes from the gateway, not the client");
    }

    TenantContext.set(sid, null);
    try {
      List<LineInput> lines = req.items().stream()
          .map(i -> new LineInput(i.variantId(), i.qty()))
          .toList();
      Long shippingRateId = parseId(req.rateId());
      var orderReq = new CreateOrderRequest(
          req.customerId(),
          req.customer().email().toLowerCase().trim(),
          req.customer().phone() == null ? null : req.customer().phone().trim(),
          lines,
          shippingRateId,
          method,
          null,
          null, // shipping address built from zone at fulfillment
          null,
          req.note() == null ? null : req.note().trim(),
          "storefront",
          req.code(),
          req.giftCode(),
          req.loyaltyPoints());
      OrderDto order = orderService.create(orderReq);
      // Manual TrxID is stored for merchant verification (not trusted as payment).
      return Map.of(
          "id", order.id(),
          "orderNumber", order.orderNumber(),
          "totalMinor", order.totalMinor(),
          "currency", order.currency(),
          "paymentMethod", method,
          "status", "sslcommerz".equals(method) ? "pending_payment" : order.financialStatus());
    } finally {
      TenantContext.clear();
    }
  }

  private Long parseId(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      // Frontend may send non-numeric demo ids; treat as absent.
      return null;
    }
  }
}
