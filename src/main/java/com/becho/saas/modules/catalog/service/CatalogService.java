package com.becho.saas.modules.catalog.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.KeysetPage;
import com.becho.saas.modules.catalog.entity.*;
import com.becho.saas.modules.catalog.repository.*;
import com.becho.saas.modules.catalog.web.CatalogDtos.*;
import com.becho.saas.modules.inventory.entity.Location;
import com.becho.saas.modules.inventory.repository.InventoryLevelRepository;
import com.becho.saas.modules.inventory.repository.LocationRepository;
import com.becho.saas.modules.inventory.service.InventoryService;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Single Responsibility: catalog aggregate only. All reads/writes store-scoped. */
@Service
public class CatalogService {
    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CollectionRepository collections;
    private final StoreRepository stores;
    private final ProductCacheEvictor cacheEvictor;
    // BUG-29 fix: variant stock is persisted in inventory_levels (per location)
    // instead of being silently dropped.
    private final InventoryService inventory;
    private final LocationRepository locations;
    private final InventoryLevelRepository levels;

    public CatalogService(ProductRepository products, ProductVariantRepository variants,
                          CollectionRepository collections, StoreRepository stores,
                          ProductCacheEvictor cacheEvictor, InventoryService inventory,
                          LocationRepository locations, InventoryLevelRepository levels) {
        this.products = products;
        this.variants = variants;
        this.collections = collections;
        this.stores = stores;
        this.cacheEvictor = cacheEvictor;
        this.inventory = inventory;
        this.locations = locations;
        this.levels = levels;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product", key = "'list:' + T(com.becho.saas.tenancy.TenantContext).requireStoreId() + ':' + #status + ':' + #cursor + ':' + #size")
    public KeysetPage<ProductDto> listProducts(String status, String cursor, Integer size) {
        Long storeId = TenantContext.requireStoreId();
        int pageSize = Math.min(size == null ? KeysetPage.DEFAULT_SIZE : size, KeysetPage.MAX_SIZE);
        var decoded = KeysetPage.decode(cursor);
        LocalDateTime cursorAt = decoded == null ? null : decoded.createdAt();
        Long cursorId = decoded == null ? null : decoded.id();

        List<Product> rows = products.findPage(storeId, status, cursorAt, cursorId, Limit.of(pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        List<Product> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            var last = page.get(page.size() - 1);
            nextCursor = KeysetPage.encode(last.getCreatedAt(), last.getId());
        }
        return new KeysetPage<>(page.stream().map(this::toDto).toList(), nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product", key = "'get:' + T(com.becho.saas.tenancy.TenantContext).requireStoreId() + ':' + #id")
    public ProductDto getProduct(Long id) {
        return toDto(findProduct(id));
    }

    @Transactional
    public ProductDto createProduct(CreateProductRequest req) {
        Long storeId = TenantContext.requireStoreId();
        Store store = stores.findById(storeId).orElseThrow(() -> ApiException.notFound("Store"));

        Product p = new Product();
        p.setStore(store);
        p.setTitle(req.title().trim());
        p.setSlug(slugify(req.title()));
        p.setDescription(req.description());
        p.setStatus(req.status() == null ? "draft" : req.status());
        p.setProductType(req.productType());
        p.setVendor(req.vendor());
        if ("active".equals(p.getStatus())) p.setPublishedAt(LocalDateTime.now());

        if (req.variants() == null || req.variants().isEmpty()) {
            throw ApiException.badRequest("NO_VARIANTS", "At least one variant is required");
        }
        List<ProductVariant> created = new ArrayList<>();
        int pos = 0;
        for (var v : req.variants()) {
            if (v.priceMinor() < 0) throw ApiException.badRequest("NEGATIVE_PRICE", "Price cannot be negative");
            ProductVariant variant = new ProductVariant();
            variant.setProduct(p);
            variant.setStore(store);
            // BUG-46 fix: empty SKU ("") violates uq_variants_sku unique constraint
            // when multiple variants have no SKU. Use null instead (NULLs are
            // allowed multiple times in a unique column).
            String sku = v.sku();
            variant.setSku(sku == null || sku.isBlank() ? null : sku.trim());
            variant.setOption1(v.option1());
            variant.setOption2(v.option2());
            variant.setOption3(v.option3());
            variant.setPriceMinor(v.priceMinor());
            variant.setCompareAtMinor(v.compareAtMinor());
            variant.setPosition(pos++);
            p.getVariants().add(variant);
            created.add(variant);
        }
        syncImages(p, req.images());
        // Flush so new variants have IDs before we write their inventory levels.
        Product saved = products.saveAndFlush(p);
        // BUG-29 fix: persist the per-variant stock the frontend sent.
        adjustStock(store, req.variants(), created, "product-create");
        ProductDto dto = toDto(saved);
        cacheEvictor.evictStore(TenantContext.requireStoreId());
        return dto;
    }

    @Transactional
    public ProductDto updateProduct(Long id, CreateProductRequest req) {
        Product p = findProduct(id);
        Store store = p.getStore();
        p.setTitle(req.title().trim());
        p.setDescription(req.description());
        if (req.status() != null) {
            p.setStatus(req.status());
            if ("active".equals(req.status()) && p.getPublishedAt() == null) {
                p.setPublishedAt(LocalDateTime.now());
            }
        }
        p.setProductType(req.productType());
        p.setVendor(req.vendor());
        // BUG-29 fix: variants were never synced on update (price/sku/stock edits
        // silently lost). Upsert by id; new client-side ids create new variants.
        List<ProductVariant> synced = syncVariants(store, p, req.variants());
        // Only replace images when the request carries the field; a null list
        // (e.g. callers that don't manage images) must not wipe existing photos.
        if (req.images() != null) {
            syncImages(p, req.images());
        }
        Product saved = products.saveAndFlush(p);
        adjustStock(store, req.variants(), synced, "product-update");
        ProductDto dto = toDto(saved);
        cacheEvictor.evictStore(TenantContext.requireStoreId());
        return dto;
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product p = findProduct(id);
        p.setDeletedAt(LocalDateTime.now());
        p.getVariants().forEach(v -> v.setDeletedAt(LocalDateTime.now()));
        products.save(p);
        cacheEvictor.evictStore(TenantContext.requireStoreId());
    }

    private Product findProduct(Long id) {
        return products.findByIdAndStoreIdAndDeletedAtIsNull(id, TenantContext.requireStoreId())
                .orElseThrow(() -> ApiException.notFound("Product"));
    }

    private ProductDto toDto(Product p) {
        List<ProductVariant> variants = p.getVariants().stream()
                .filter(v -> v.getDeletedAt() == null)
                .toList();
        // BUG-29 fix: read back the persisted stock (on-hand summed across locations).
        Map<Long, Integer> stock = stockByVariantIds(variants.stream()
                .map(ProductVariant::getId).filter(Objects::nonNull).toList());
        var vDtos = variants.stream()
                .map(v -> new VariantDto(v.getId(), v.getSku(), v.getOption1(), v.getOption2(), v.getOption3(),
                        v.getPriceMinor(), v.getCompareAtMinor(), v.getPosition(),
                        v.getId() == null ? 0 : stock.getOrDefault(v.getId(), 0)))
                .toList();
        var imgUrls = p.getImages().stream()
                .sorted(java.util.Comparator.comparingInt(ProductImage::getPosition))
                .map(ProductImage::getUrl)
                .toList();
        return new ProductDto(p.getId(), p.getTitle(), p.getSlug(), p.getDescription(), p.getStatus(),
                p.getProductType(), p.getVendor(), p.getPublishedAt(), vDtos, imgUrls);
    }

    /**
     * Upserts the product's variants from the request, in input order.
     * A null input list means the caller doesn't manage variants — leave them untouched.
     * Variants absent from the input are intentionally kept (not deleted): the frontend
     * always sends the full list, and partial API callers must not lose data.
     * Returns the variant entities in input order (for stock sync after flush).
     */
    private List<ProductVariant> syncVariants(Store store, Product p, List<VariantInput> inputs) {
        List<ProductVariant> ordered = new ArrayList<>();
        if (inputs == null) {
            p.getVariants().stream()
                    .filter(v -> v.getDeletedAt() == null)
                    .sorted(Comparator.comparingInt(ProductVariant::getPosition))
                    .forEach(ordered::add);
            return ordered;
        }
        Map<Long, ProductVariant> existing = new HashMap<>();
        for (ProductVariant v : p.getVariants()) {
            if (v.getDeletedAt() == null && v.getId() != null) existing.put(v.getId(), v);
        }
        int pos = 0;
        for (VariantInput in : inputs) {
            ProductVariant v = null;
            if (in.id() != null && !in.id().isBlank()) {
                try {
                    v = existing.get(Long.valueOf(in.id().trim()));
                } catch (NumberFormatException ignored) {
                    // New client-side id (UUID) -> create a fresh variant below.
                }
            }
            if (v == null) {
                v = new ProductVariant();
                v.setProduct(p);
                v.setStore(store);
                p.getVariants().add(v);
            }
            if (in.priceMinor() < 0) throw ApiException.badRequest("NEGATIVE_PRICE", "Price cannot be negative");
            String sku = in.sku();
            v.setSku(sku == null || sku.isBlank() ? null : sku.trim());
            v.setOption1(in.option1());
            v.setOption2(in.option2());
            v.setOption3(in.option3());
            v.setPriceMinor(in.priceMinor());
            v.setCompareAtMinor(in.compareAtMinor());
            v.setPosition(pos++);
            ordered.add(v);
        }
        return ordered;
    }

    /**
     * BUG-29 fix: writes each variant's stock to the store's default location
     * (creating the inventory level row on first use). Inputs and variants must
     * be in the same order.
     */
    private void adjustStock(Store store, List<VariantInput> inputs, List<ProductVariant> variants, String reason) {
        if (inputs == null || inputs.isEmpty() || variants.isEmpty()) return;
        try {
            Location loc = defaultLocation(store);
            for (int i = 0; i < Math.min(inputs.size(), variants.size()); i++) {
                ProductVariant v = variants.get(i);
                if (v.getId() != null) {
                    inventory.adjust(loc.getId(), v.getId(), Math.max(0, inputs.get(i).stock()), reason);
                }
            }
        } catch (Exception e) {
            // BUG-29 resilience: if inventory tables are unavailable, don't fail
            // product creation — stock will read as 0 until inventory is fixed.
            log.warn("adjustStock skipped: {}", e.getMessage());
        }
    }

    /** The store's default location; creates one on first use so stock always has a home. */
    private Location defaultLocation(Store store) {
        List<Location> locs = locations.findByStoreIdAndDeletedAtIsNull(store.getId());
        return locs.stream().filter(Location::isDefaultLocation).findFirst()
                .orElseGet(() -> locs.stream().findFirst().orElseGet(() -> {
                    Location l = new Location();
                    l.setStore(store);
                    l.setName("Default");
                    l.setDefaultLocation(true);
                    return locations.save(l);
                }));
    }

    private Map<Long, Integer> stockByVariantIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            Map<Long, Integer> m = new HashMap<>();
            for (Object[] row : levels.sumOnHandByVariantIds(ids)) {
                m.put((Long) row[0], ((Number) row[1]).intValue());
            }
            return m;
        } catch (Exception e) {
            // BUG-29 resilience: inventory tables unavailable — report zero stock.
            log.warn("stockByVariantIds skipped: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Replaces the product's images with the given URLs. {@code Product.images}
     * is {@code orphanRemoval=true}, so clearing the collection deletes the old
     * rows and cascade-persist saves the new ones in the same transaction.
     * A null list leaves existing images untouched; URLs are sanitized
     * (non-blank, http(s), max 1024 chars, max 10, de-duplicated).
     */
    private void syncImages(Product p, List<String> urls) {
        if (urls == null) {
            return;
        }
        p.getImages().clear();
        int pos = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String raw : urls) {
            if (pos >= 10) {
                break;
            }
            if (raw == null) {
                continue;
            }
            String u = raw.trim();
            if (u.isEmpty() || u.length() > 1024 || seen.contains(u)) {
                continue;
            }
            String lower = u.toLowerCase(java.util.Locale.ROOT);
            if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
                continue;
            }
            seen.add(u);
            ProductImage img = new ProductImage();
            img.setProduct(p);
            img.setUrl(u);
            img.setPosition(pos++);
            // BUG-47 fix: the image was never added to the product's collection!
            p.getImages().add(img);
        }
    }

    private static String slugify(String s) {
        return s.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
