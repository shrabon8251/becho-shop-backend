package com.becho.saas.modules.customers.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public class CustomerDtos {

  public record CreateCustomerRequest(
      @Email String email,
      String phone,
      String firstName,
      String lastName,
      Boolean acceptsMarketing,
      String note) {}

  public record CustomerDto(Long id, String email, String phone, String firstName, String lastName,
                            long totalSpentMinor, int ordersCount, String status,
                            LocalDateTime createdAt, long creditMinor) {}
}
