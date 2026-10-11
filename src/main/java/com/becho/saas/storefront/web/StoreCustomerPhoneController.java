package com.becho.saas.storefront.web;

import com.becho.saas.modules.customers.service.CustomerOtpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Shopper self-service phone verification: customers verify their own
 * phone number via OTP without merchant intervention.
 *
 * <p>Rate-limited (OTP bucket). OTPs are hash-only, 10-minute expiry,
 * single-use.
 */
@RestController
@RequestMapping("/stores/{storeId}/account/phone")
public class StoreCustomerPhoneController {

  private final CustomerOtpService otpService;
  private final StoreScope scope;

  public StoreCustomerPhoneController(CustomerOtpService otpService, StoreScope scope) {
    this.otpService = otpService;
    this.scope = scope;
  }

  public record SendOtpRequest(
      @Email @NotBlank String email,
      @NotBlank String phone) {}

  public record VerifyOtpRequest(
      @Email @NotBlank String email,
      @NotBlank String code) {}

  @PostMapping("/send-otp")
  public Map<String, Object> sendOtp(@PathVariable String storeId,
                                     @Valid @RequestBody SendOtpRequest req) {
    Long sid = StoreScope.lid(storeId);
    otpService.sendOtp(sid, req.email(), req.phone());
    return Map.of("sent", true);
  }

  @PostMapping("/verify-otp")
  public Map<String, Object> verifyOtp(@PathVariable String storeId,
                                       @Valid @RequestBody VerifyOtpRequest req) {
    Long sid = StoreScope.lid(storeId);
    otpService.verifyOtp(sid, req.email(), req.code());
    return Map.of("verified", true);
  }
}
