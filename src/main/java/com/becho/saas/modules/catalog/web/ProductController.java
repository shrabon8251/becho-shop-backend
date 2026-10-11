package com.becho.saas.modules.catalog.web;

import com.becho.saas.common.ApiResponse;
import com.becho.saas.common.KeysetPage;
import com.becho.saas.modules.catalog.service.CatalogService;
import com.becho.saas.modules.catalog.web.CatalogDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final CatalogService catalogService;

  public ProductController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public ApiResponse<KeysetPage<ProductDto>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(catalogService.listProducts(status, cursor, size));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public ApiResponse<ProductDto> get(@PathVariable Long id) {
    return ApiResponse.ok(catalogService.getProduct(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public ApiResponse<ProductDto> create(@Valid @RequestBody CreateProductRequest req) {
    return ApiResponse.ok(catalogService.createProduct(req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public ApiResponse<ProductDto> update(@PathVariable Long id,
                                        @Valid @RequestBody CreateProductRequest req) {
    return ApiResponse.ok(catalogService.updateProduct(id, req));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    catalogService.deleteProduct(id);
    return ApiResponse.ok(null);
  }
}
