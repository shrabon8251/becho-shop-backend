package com.becho.saas.storefront.web;

import com.becho.saas.modules.catalog.service.CatalogService;
import com.becho.saas.modules.catalog.web.CatalogDtos.CreateProductRequest;
import com.becho.saas.modules.catalog.web.CatalogDtos.ProductDto;
import com.becho.saas.modules.catalog.web.CatalogDtos.VariantInput;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible product endpoints: /stores/{storeId}/products[/{id}].
 * Returns the frontend's Product shape directly (not wrapped in ApiResponse).
 */
@RestController
@RequestMapping("/stores/{storeId}/products")
public class StoreProductController {

    private final CatalogService catalogService;
    private final StoreScope scope;

    public StoreProductController(CatalogService catalogService, StoreScope scope) {
        this.catalogService = catalogService;
        this.scope = scope;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public List<Map<String, Object>> list(@PathVariable String storeId) {
        scope.require(storeId);
        var page = catalogService.listProducts(null, null, 100);
        return page.items().stream().map(this::toFrontend).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public Map<String, Object> get(@PathVariable String storeId, @PathVariable Long id) {
        scope.require(storeId);
        return toFrontend(catalogService.getProduct(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> create(@PathVariable String storeId,
                                      @RequestBody Map<String, Object> body) {
        scope.require(storeId);
        return toFrontend(catalogService.createProduct(toCreateRequest(body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> save(@PathVariable String storeId,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        scope.require(storeId);
        try {
            return toFrontend(catalogService.updateProduct(id, toCreateRequest(body)));
        } catch (Exception e) {
            return toFrontend(catalogService.createProduct(toCreateRequest(body)));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void remove(@PathVariable String storeId, @PathVariable Long id) {
        scope.require(storeId);
        catalogService.deleteProduct(id);
    }

    @SuppressWarnings("unchecked")
    private CreateProductRequest toCreateRequest(Map<String, Object> body) {
        String title = (String) body.getOrDefault("title", "Untitled");
        String description = (String) body.get("description");
        String status = (String) body.getOrDefault("status", "Draft");
        String productType = (String) body.get("productType");
        String vendor = (String) body.get("vendor");
        List<Map<String, Object>> variants = (List<Map<String, Object>>) body.get("variants");
        List<VariantInput> inputs;
        if (variants == null || variants.isEmpty()) {
            Object priceObj = body.get("price");
            long priceMinor = priceObj instanceof Number n ? n.longValue() : 0;
            inputs = List.of(new VariantInput(null, "DEFAULT", null, null, null, priceMinor, null, 0));
        } else {
            inputs = variants.stream().map(v -> new VariantInput(
                    // BUG-29 fix: pass through the variant id (update matches on it)
                    // and stock (persisted to the default location's inventory level).
                    (String) v.get("id"),
                    (String) v.getOrDefault("sku", ""),
                    (String) v.get("option1"),
                    (String) v.get("option2"),
                    (String) v.get("option3"),
                    v.get("price") instanceof Number n ? n.longValue() : 0,
                    v.get("compareAt") instanceof Number n ? n.longValue() : null,
                    v.get("stock") instanceof Number n ? n.intValue() : 0
            )).collect(Collectors.toList());
        }
        List<String> images = (List<String>) body.get("images");
        return new CreateProductRequest(title, description, status, productType, vendor, inputs, images);
    }

    private Map<String, Object> toFrontend(ProductDto p) {
        List<Map<String, Object>> variants = p.variants().stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.id().toString());
            m.put("sku", v.sku());
            m.put("price", v.priceMinor());
            m.put("compareAt", v.compareAtMinor());
            m.put("option1", v.option1());
            m.put("option2", v.option2());
            m.put("option3", v.option3());
            // BUG-29 fix: return the persisted stock so the list/form show real numbers.
            m.put("stock", v.stock());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id().toString());
        m.put("title", p.title());
        m.put("status", "active".equalsIgnoreCase(p.status()) ? "Active" : "Draft");
        m.put("description", p.description());
        m.put("vendor", p.vendor());
        m.put("productType", p.productType());
        m.put("images", p.images());
        m.put("variants", variants);
        m.put("collectionId", "");
        return m;
    }
}
