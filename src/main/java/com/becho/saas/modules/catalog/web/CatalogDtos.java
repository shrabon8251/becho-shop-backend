package com.becho.saas.modules.catalog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.List;

// BUG-29 fix: VariantInput/VariantDto now carry the variant's id (so updates can
// match existing variants instead of dropping them) and stock (persisted to the
// store's default location via InventoryService, read back as on-hand sum).
public class CatalogDtos {

    public record VariantInput(String id, String sku, String option1, String option2, String option3,
                               long priceMinor, Long compareAtMinor, int stock) {}

    public record CreateProductRequest(
            @NotBlank String title,
            String description,
            String status,
            String productType,
            String vendor,
            @NotEmpty List<VariantInput> variants,
            List<String> images) {}

    public record VariantDto(Long id, String sku, String option1, String option2, String option3,
                             long priceMinor, Long compareAtMinor, int position, int stock) {}

    public record ProductDto(Long id, String title, String slug, String description, String status,
                             String productType, String vendor, LocalDateTime publishedAt,
                             List<VariantDto> variants, List<String> images) {}
}
