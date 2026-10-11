package com.becho.saas.modules.orders.web;

import com.becho.saas.common.ApiResponse;
import com.becho.saas.common.KeysetPage;
import com.becho.saas.modules.orders.entity.Order;
import com.becho.saas.modules.orders.repository.OrderRepository;
import com.becho.saas.modules.orders.service.OrderService;
import com.becho.saas.modules.orders.web.OrderDtos.*;
import com.becho.saas.tenancy.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderService orderService;
  private final OrderRepository orders;

  public OrderController(OrderService orderService, OrderRepository orders) {
    this.orderService = orderService;
    this.orders = orders;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public ApiResponse<KeysetPage<OrderDto>> list(
      @RequestParam(required = false) String financial,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    Long storeId = TenantContext.requireStoreId();
    int pageSize = Math.min(size == null ? KeysetPage.DEFAULT_SIZE : size, KeysetPage.MAX_SIZE);
    var decoded = KeysetPage.decode(cursor);
    List<Order> rows = orders.findPage(storeId, financial,
        decoded == null ? null : decoded.createdAt(),
        decoded == null ? null : decoded.id(),
        Limit.of(pageSize + 1));
    boolean hasMore = rows.size() > pageSize;
    List<Order> page = hasMore ? rows.subList(0, pageSize) : rows;
    String nextCursor = null;
    if (hasMore && !page.isEmpty()) {
      var last = page.get(page.size() - 1);
      nextCursor = KeysetPage.encode(last.getCreatedAt(), last.getId());
    }
    // lightweight list DTO (no lines)
    var items = page.stream().map(o -> new OrderDto(o.getId(), o.getOrderNumber(),
        o.getFinancialStatus(), o.getFulfillmentStatus(), o.getSubtotalMinor(),
        o.getDiscountMinor(), o.getShippingMinor(), o.getTaxMinor(), o.getTotalMinor(),
        o.getCurrency(), o.getPaymentMethod(), o.getEmail(), o.getPhone(),
        o.getCreatedAt(), List.of(), o.getDiscountCode(), o.getGiftAppliedMinor(),
        o.getLoyaltyPointsUsed(), o.getLoyaltyPointsEarned())).toList();
    return ApiResponse.ok(new KeysetPage<>(items, nextCursor, hasMore));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public ApiResponse<OrderDto> get(@PathVariable Long id) {
    return ApiResponse.ok(orderService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public ApiResponse<OrderDto> create(@Valid @RequestBody CreateOrderRequest req) {
    return ApiResponse.ok(orderService.create(req));
  }

  @PostMapping("/{id}/transactions")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public ApiResponse<OrderDto> recordTransaction(@PathVariable Long id,
                                                 @Valid @RequestBody RecordTransactionRequest req) {
    return ApiResponse.ok(orderService.recordTransaction(id, req));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public ApiResponse<OrderDto> cancel(@PathVariable Long id) {
    return ApiResponse.ok(orderService.cancel(id));
  }
}
