package com.becho.saas.modules.customers.web;

import com.becho.saas.common.ApiResponse;
import com.becho.saas.common.KeysetPage;
import com.becho.saas.modules.customers.service.CustomerService;
import com.becho.saas.modules.customers.web.CustomerDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public ApiResponse<KeysetPage<CustomerDto>> list(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(customerService.list(cursor, size));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public ApiResponse<CustomerDto> get(@PathVariable Long id) {
    return ApiResponse.ok(customerService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public ApiResponse<CustomerDto> create(@Valid @RequestBody CreateCustomerRequest req) {
    return ApiResponse.ok(customerService.create(req));
  }
}
