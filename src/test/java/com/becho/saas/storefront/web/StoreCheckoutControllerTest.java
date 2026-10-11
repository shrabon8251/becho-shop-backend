package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.orders.service.OrderService;
import com.becho.saas.modules.orders.web.OrderDtos.OrderDto;
import com.becho.saas.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Public checkout: payment method allowlist, manual TrxID required,
 * gateway TrxID rejected (it comes from the gateway, not the client).
 */
class StoreCheckoutControllerTest {

  private final OrderService orderService = mock(OrderService.class);
  private final StoreScope scope = mock(StoreScope.class);
  private final StoreCheckoutController controller =
      new StoreCheckoutController(orderService, scope);

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private StoreCheckoutController.CheckoutRequest req(String method, String trxId) {
    when(scope.require("1")).thenReturn(1L);
    return new StoreCheckoutController.CheckoutRequest(
        new StoreCheckoutController.CustomerInput("Rahim", "rahim@test.com", "01700000000"),
        List.of(new StoreCheckoutController.ItemInput(10L, 2)),
        method, null, null, null, null, null, trxId, null, null);
  }

  private OrderDto orderDto() {
    return new OrderDto(5L, 1001L, "pending", "unfulfilled",
        10000, 0, 500, 0, 10500, "BDT", "cod",
        "rahim@test.com", "01700000000", LocalDateTime.now(), List.of(), null, 0, 0, 0);
  }

  @Test
  void rejectsUnknownPaymentMethod() {
    ApiException e = assertThrows(ApiException.class,
        () -> controller.checkout("1", req("bitcoin", null)));
    assertEquals("BAD_PAYMENT_METHOD", e.getCode());
    verifyNoInteractions(orderService);
  }

  @Test
  void manualMethodRequiresTrxId() {
    ApiException e = assertThrows(ApiException.class,
        () -> controller.checkout("1", req("manual-bkash", null)));
    assertEquals("TRXID_REQUIRED", e.getCode());
    verifyNoInteractions(orderService);
  }

  @Test
  void gatewayMethodRejectsClientSuppliedTrxId() {
    ApiException e = assertThrows(ApiException.class,
        () -> controller.checkout("1", req("sslcommerz", "FAKE123")));
    assertEquals("BAD_TRXID", e.getCode());
    verifyNoInteractions(orderService);
  }

  @Test
  void codOrderSucceeds() {
    when(orderService.create(any())).thenReturn(orderDto());
    var result = controller.checkout("1", req("cod", null));
    assertEquals(5L, result.get("id"));
    assertEquals("cod", result.get("paymentMethod"));
    verify(orderService).create(any());
  }

  @Test
  void manualOrderWithTrxIdSucceeds() {
    when(orderService.create(any())).thenReturn(orderDto());
    var result = controller.checkout("1", req("manual-nagad", "NAGAD9X8Y7Z"));
    assertEquals(5L, result.get("id"));
    assertEquals("manual-nagad", result.get("paymentMethod"));
  }

  @Test
  void sslcommerzOrderMarkedPendingPayment() {
    when(orderService.create(any())).thenReturn(orderDto());
    var result = controller.checkout("1", req("sslcommerz", null));
    assertEquals("pending_payment", result.get("status"));
  }
}
