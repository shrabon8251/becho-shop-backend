package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.orders.entity.Order;
import com.becho.saas.modules.orders.repository.OrderRepository;
import com.becho.saas.modules.orders.repository.OrderTransactionRepository;
import com.becho.saas.modules.payments.PaymentProvider;
import com.becho.saas.modules.payments.PaymentProviderRegistry;
import com.becho.saas.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Shopper-facing payment intents for gateway checkout (SSLCommerz).
 *
 * <p>Flow:
 * <ol>
 *   <li>Frontend creates a pending order via {@code POST /checkout/orders}.</li>
 *   <li>Frontend calls this endpoint with {@code { orderId, provider }}.</li>
 *   <li>Backend validates the order (belongs to this store, still pending,
 *       totals match) and asks SSLCommerz for a gateway session.</li>
 *   <li>Frontend redirects the shopper to {@code redirectUrl}.</li>
 *   <li>SSLCommerz calls back; {@code PaymentController} verifies
 *       server-to-server before marking anything paid.</li>
 * </ol>
 *
 * <p>Security: rate-limited (public bucket); the order total is re-read
 * from the database, never trusted from the client; the gateway tran_id
 * embeds the order id so callbacks cannot be replayed across orders.
 */
@RestController
@RequestMapping("/stores/{storeId}/payments")
public class StorePaymentIntentController {

  private final PaymentProviderRegistry registry;
  private final OrderRepository orders;
  private final OrderTransactionRepository transactions;
  private final StoreScope scope;

  /** Public storefront base URL used for gateway return links. */
  @Value("${becho.storefront.url:}")
  private String storefrontUrl;

  public StorePaymentIntentController(PaymentProviderRegistry registry,
                                      OrderRepository orders,
                                      OrderTransactionRepository transactions,
                                      StoreScope scope) {
    this.registry = registry;
    this.orders = orders;
    this.transactions = transactions;
    this.scope = scope;
  }

  public record IntentRequest(
      @NotNull Long orderId,
      @NotBlank String provider) {}

  @PostMapping("/intents")
  public Map<String, Object> createIntent(@PathVariable String storeId,
                                          @Valid @RequestBody IntentRequest req) {
    Long sid = StoreScope.lid(storeId);
    String providerKey = req.provider().trim().toLowerCase();
    if (!"sslcommerz".equals(providerKey)) {
      throw ApiException.badRequest("BAD_PROVIDER",
          "Only 'sslcommerz' gateway intents are supported");
    }

    Order order = orders.findByIdAndStoreIdAndDeletedAtIsNull(req.orderId(), sid)
        .orElseThrow(() -> ApiException.notFound("Order"));
    if (!"sslcommerz".equalsIgnoreCase(order.getPaymentMethod())) {
      throw ApiException.badRequest("WRONG_METHOD",
          "Order was not placed with SSLCommerz");
    }
    if (order.getCancelledAt() != null) {
      throw ApiException.badRequest("ORDER_CANCELLED", "Order is cancelled");
    }
    if (isAlreadyPaid(order)) {
      throw ApiException.badRequest("ALREADY_PAID", "Order is already paid");
    }

    PaymentProvider provider = registry.get(providerKey);
    String base = storefrontBase();
    TenantContext.set(sid, null);
    try {
      var result = provider.initPayment(new PaymentProvider.InitRequest(
          sid,
          order.getId(),
          order.getTotalMinor(),
          order.getCurrency(),
          order.getEmail(),
          order.getPhone(),
          base + "/payment/success?order=" + order.getId(),
          base + "/payment/failed?order=" + order.getId(),
          base + "/payment/cancelled?order=" + order.getId()));
      return Map.of(
          "redirectUrl", result.checkoutUrl(),
          "orderId", order.getId(),
          "amountMinor", order.getTotalMinor(),
          "currency", order.getCurrency());
    } finally {
      TenantContext.clear();
    }
  }

  private boolean isAlreadyPaid(Order order) {
    return transactions.findByOrderIdOrderByCreatedAt(order.getId()).stream()
        .anyMatch(t -> "sale".equalsIgnoreCase(t.getKind())
            && "success".equalsIgnoreCase(t.getStatus()));
  }

  private String storefrontBase() {
    if (storefrontUrl != null && !storefrontUrl.isBlank()) {
      return storefrontUrl.replaceAll("/+$", "");
    }
    // Fallback: the callback IPN is what actually confirms payment;
    // these links are only UX. Default to relative paths.
    return "";
  }
}
