package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.orders.entity.Order;
import com.becho.saas.modules.orders.entity.OrderTransaction;
import com.becho.saas.modules.orders.repository.OrderRepository;
import com.becho.saas.modules.orders.repository.OrderTransactionRepository;
import com.becho.saas.modules.payments.PaymentProvider;
import com.becho.saas.modules.payments.PaymentProviderRegistry;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Payment intents: only pending sslcommerz orders in the URL's store get a
 * gateway redirect. Wrong method, cancelled, or already-paid orders are
 * rejected. Amounts come from the database, never the client.
 */
class StorePaymentIntentControllerTest {

  private final PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
  private final OrderRepository orders = mock(OrderRepository.class);
  private final OrderTransactionRepository transactions =
      mock(OrderTransactionRepository.class);
  private final StoreScope scope = mock(StoreScope.class);
  private final StorePaymentIntentController controller =
      new StorePaymentIntentController(registry, orders, transactions, scope);

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private Order order(long id, String paymentMethod, boolean cancelled) throws Exception {
    Order o = new Order();
    setField(o, "id", id);
    o.setPaymentMethod(paymentMethod);
    o.setTotalMinor(10500);
    o.setCurrency("BDT");
    o.setEmail("rahim@test.com");
    o.setPhone("01700000000");
    Store store = new Store();
    setField(store, "id", 1L);
    o.setStore(store);
    if (cancelled) {
      o.setCancelledAt(java.time.LocalDateTime.now());
    }
    return o;
  }

  private void setField(Object target, String name, Object value) throws Exception {
    Class<?> c = target.getClass();
    while (c != null) {
      try {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException e) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  private void setupOrder(Order o) {
    when(scope.require("1")).thenReturn(1L);
    when(orders.findByIdAndStoreIdAndDeletedAtIsNull(o.getId(), 1L))
        .thenReturn(Optional.of(o));
    when(transactions.findByOrderIdOrderByCreatedAt(o.getId()))
        .thenReturn(List.of());
  }

  @Test
  void rejectsNonSslcommerzProvider() {
    assertThrows(ApiException.class, () -> controller.createIntent("1",
        new StorePaymentIntentController.IntentRequest(5L, "bkash")));
    verifyNoInteractions(registry);
  }

  @Test
  void rejectsOrderWithWrongPaymentMethod() throws Exception {
    Order o = order(5L, "cod", false);
    setupOrder(o);
    ApiException e = assertThrows(ApiException.class, () -> controller.createIntent("1",
        new StorePaymentIntentController.IntentRequest(5L, "sslcommerz")));
    assertEquals("WRONG_METHOD", e.getCode());
  }

  @Test
  void rejectsCancelledOrder() throws Exception {
    Order o = order(5L, "sslcommerz", true);
    setupOrder(o);
    ApiException e = assertThrows(ApiException.class, () -> controller.createIntent("1",
        new StorePaymentIntentController.IntentRequest(5L, "sslcommerz")));
    assertEquals("ORDER_CANCELLED", e.getCode());
  }

  @Test
  void rejectsAlreadyPaidOrder() throws Exception {
    Order o = order(5L, "sslcommerz", false);
    setupOrder(o);
    OrderTransaction t = new OrderTransaction();
    t.setKind("sale");
    t.setStatus("success");
    when(transactions.findByOrderIdOrderByCreatedAt(5L)).thenReturn(List.of(t));
    ApiException e = assertThrows(ApiException.class, () -> controller.createIntent("1",
        new StorePaymentIntentController.IntentRequest(5L, "sslcommerz")));
    assertEquals("ALREADY_PAID", e.getCode());
  }

  @Test
  void returnsRedirectUrlForPendingOrder() throws Exception {
    Order o = order(5L, "sslcommerz", false);
    setupOrder(o);
    PaymentProvider provider = mock(PaymentProvider.class);
    when(registry.get("sslcommerz")).thenReturn(provider);
    when(provider.initPayment(any())).thenReturn(
        new PaymentProvider.InitResult("https://gateway.example/pay/abc", "sess123", null));
    var result = controller.createIntent("1",
        new StorePaymentIntentController.IntentRequest(5L, "sslcommerz"));
    assertEquals("https://gateway.example/pay/abc", result.get("redirectUrl"));
    assertEquals(5L, result.get("orderId"));
    assertEquals(10500L, result.get("amountMinor"));
    // The init request carries DB totals, not client input.
    verify(provider).initPayment(argThat(req ->
        req.orderId() == 5L && req.amountMinor() == 10500L && "BDT".equals(req.currency())));
  }

  @Test
  void rejectsUnknownOrder() {
    when(scope.require("1")).thenReturn(1L);
    when(orders.findByIdAndStoreIdAndDeletedAtIsNull(99L, 1L))
        .thenReturn(Optional.empty());
    assertThrows(ApiException.class, () -> controller.createIntent("1",
        new StorePaymentIntentController.IntentRequest(99L, "sslcommerz")));
  }
}
