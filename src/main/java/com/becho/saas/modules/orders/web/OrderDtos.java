package com.becho.saas.modules.orders.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDtos {

  public record LineInput(Long variantId, int quantity) {}

  public record CreateOrderRequest(
      Long customerId,
      String email,
      String phone,
      @NotEmpty List<LineInput> lines,
      Long locationId,
      String paymentMethod,
      Long shippingRateId,
      String shippingAddress,
      String billingAddress,
      String notes,
      String source,
      String discountCode,
      String giftCode,
      Integer loyaltyPoints) {}

  public record RecordTransactionRequest(
      @NotBlank String kind,
      @NotBlank String gateway,
      long amountMinor,
      @NotBlank String status,
      String gatewayTxnId,
      String raw) {}

  public record LineDto(Long id, Long variantId, String title, String sku,
                        int quantity, long priceMinor, long totalMinor) {}

  public record OrderDto(Long id, long orderNumber, String financialStatus, String fulfillmentStatus,
                         long subtotalMinor, long discountMinor, long shippingMinor, long taxMinor,
                         long totalMinor, String currency, String paymentMethod,
                         String email, String phone, LocalDateTime createdAt,
                         List<LineDto> lines, String discountCode, long giftAppliedMinor,
                         int loyaltyPointsUsed, int loyaltyPointsEarned) {}
}
