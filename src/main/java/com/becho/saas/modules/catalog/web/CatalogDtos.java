package com.becho.saas.modules.catalog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.List;

public class CatalogDtos {

    public record VariantInput(String sku, String option1, String option2, String option3,
                               long priceMinor, Long compareAtMinor) {}

    public record CreateProductRequest(
            @NotBlank String title,
            String description,
            String status,
            String productType,
            String vendor,
            @NotEmpty List<VariantInput> variants,
            List<String> images) {}

    public record VariantDto(Long id, String sku, String option1, String option2, String option3,
                             long priceMinor, Long compareAtMinor, int position) {}

    public record ProductDto(Long id, String title, String slug, String description, String status,
                             String productType, String vendor, LocalDateTime publishedAt,
                             List<VariantDto> variants, List<String> images) {}
}
