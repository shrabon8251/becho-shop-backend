package com.becho.saas.modules.catalog.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.KeysetPage;
import com.becho.saas.modules.catalog.entity.*;
import com.becho.saas.modules.catalog.repository.*;
import com.becho.saas.modules.catalog.web.CatalogDtos.*;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/** Single Responsibility: catalog aggregate only. All reads/writes store-scoped. */
@Service
public class CatalogService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CollectionRepository collections;
    private final StoreRepository stores;
    private final ProductCacheEvictor cacheEvictor;

    public CatalogService(ProductRepository products, ProductVariantRepository variants,
                          CollectionRepository collections, StoreRepository stores,
                          ProductCacheEvictor cacheEvictor) {
        this.products = products;
        this.variants = variants;
        this.collections = collections;
        this.stores = stores;
        this.cacheEvictor = cacheEvictor;
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
        int pos = 0;
        for (var v : req.variants()) {
            if (v.priceMinor() < 0) throw ApiException.badRequest("NEGATIVE_PRICE", "Price cannot be negative");
            ProductVariant variant = new ProductVariant();
            variant.setProduct(p);
            variant.setStore(store);
            variant.setSku(v.sku());
            variant.setOption1(v.option1());
            variant.setOption2(v.option2());
            variant.setOption3(v.option3());
            variant.setPriceMinor(v.priceMinor());
            variant.setCompareAtMinor(v.compareAtMinor());
            variant.setPosition(pos++);
            p.getVariants().add(variant);
        }
        ProductDto dto = toDto(products.save(p));
        cacheEvictor.evictStore(TenantContext.requireStoreId());
        return dto;
    }

    @Transactional
    public ProductDto updateProduct(Long id, CreateProductRequest req) {
        Product p = findProduct(id);
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
        ProductDto dto = toDto(products.save(p));
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
        var vDtos = p.getVariants().stream()
                .filter(v -> v.getDeletedAt() == null)
                .map(v -> new VariantDto(v.getId(), v.getSku(), v.getOption1(), v.getOption2(), v.getOption3(),
                        v.getPriceMinor(), v.getCompareAtMinor(), v.getPosition()))
                .toList();
        var imgUrls = p.getImages().stream()
                .sorted(java.util.Comparator.comparingInt(ProductImage::getPosition))
                .map(ProductImage::getUrl)
                .toList();
        return new ProductDto(p.getId(), p.getTitle(), p.getSlug(), p.getDescription(), p.getStatus(),
                p.getProductType(), p.getVendor(), p.getPublishedAt(), vDtos, imgUrls);
    }

    private static String slugify(String s) {
        return s.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
