package com.becho.saas.modules.discounts.web;

import com.becho.saas.common.ApiResponse;
import com.becho.saas.modules.discounts.service.DiscountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discounts")
public class DiscountController {

  private final DiscountService discountService;

  public DiscountController(DiscountService discountService) {
    this.discountService = discountService;
  }

  public record ValidateRequest(String code, long subtotalMinor, Long customerId) {}

  @PostMapping("/validate")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public ApiResponse<DiscountService.AppliedDiscount> validate(@RequestBody ValidateRequest req) {
    return ApiResponse.ok(discountService.validate(req.code(), req.subtotalMinor(), req.customerId()));
  }
}
