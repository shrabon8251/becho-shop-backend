package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.catalog.entity.Collection;
import com.becho.saas.modules.catalog.repository.CollectionRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible collection endpoints: /stores/{storeId}/collections[/{id}].
 */
@RestController
@RequestMapping("/stores/{storeId}/collections")
public class StoreCollectionController {

    private final CollectionRepository collections;
    private final StoreRepository storeRepository;
    private final StoreScope scope;
    private final JdbcTemplate jdbc;

    public StoreCollectionController(CollectionRepository collections,
                                     StoreRepository storeRepository, StoreScope scope,
                                     JdbcTemplate jdbc) {
        this.collections = collections;
        this.storeRepository = storeRepository;
        this.scope = scope;
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public List<Map<String, Object>> list(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        return collections.findByStoreIdAndDeletedAtIsNullOrderByTitle(sid)
                .stream().map(this::toFrontend).collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> create(@PathVariable String storeId,
                                      @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        Collection c = new Collection();
        c.setStore(storeRepository.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
        applyBody(c, body);
        Collection saved = collections.save(c);
        syncProductIds(sid, saved.getId(), body.get("productIds"));
        return toFrontend(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> save(@PathVariable String storeId,
                                    @PathVariable String id,
                                    @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        Collection c;
        if (StoreScope.isNewId(id)) {
            return create(storeId, body);
        }
        c = collections.findByIdAndStoreIdAndDeletedAtIsNull(StoreScope.lid(id), sid)
                .orElseThrow(() -> ApiException.notFound("Collection"));
        applyBody(c, body);
        Collection saved = collections.save(c);
        syncProductIds(sid, saved.getId(), body.get("productIds"));
        return toFrontend(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void remove(@PathVariable String storeId, @PathVariable String id) {
        Long sid = scope.require(storeId);
        Collection c = collections.findByIdAndStoreIdAndDeletedAtIsNull(StoreScope.lid(id), sid)
                .orElseThrow(() -> ApiException.notFound("Collection"));
        c.setDeletedAt(java.time.LocalDateTime.now());
        collections.save(c);
    }

    private void applyBody(Collection c, Map<String, Object> body) {
        if (body.get("name") != null) c.setTitle((String) body.get("name"));
        if (body.get("description") != null) c.setDescription((String) body.get("description"));
        if (body.get("image") != null) c.setImageUrl((String) body.get("image"));
        if (body.get("sort") != null) c.setSortOrder((String) body.get("sort"));
    }

    /**
     * Persists manual collection membership in the {@code collection_products} join
     * table (replaces the old membership). A null/absent list leaves membership
     * untouched. Only products of this store are accepted.
     */
    private void syncProductIds(Long storeId, Long collectionId, Object raw) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        List<Long> storeProductIds = jdbc.queryForList(
                "SELECT id FROM products WHERE store_id = ? AND deleted_at IS NULL", Long.class, storeId);
        List<Long> ids = list.stream()
                .map(o -> {
                    try {
                        return Long.valueOf(String.valueOf(o));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(id -> id != null && storeProductIds.contains(id))
                .distinct()
                .toList();
        jdbc.update("DELETE FROM collection_products WHERE collection_id = ?", collectionId);
        int pos = 0;
        for (Long pid : ids) {
            jdbc.update("INSERT INTO collection_products (collection_id, product_id, position) VALUES (?,?,?)",
                    collectionId, pid, pos++);
        }
    }

    /** Manual membership for one collection, as strings (frontend compares with string ids). */
    private List<String> productIdsOf(Long collectionId) {
        return jdbc.queryForList(
                "SELECT product_id FROM collection_products WHERE collection_id = ? ORDER BY position",
                Long.class, collectionId).stream().map(String::valueOf).toList();
    }

    private Map<String, Object> toFrontend(Collection c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("name", c.getTitle());
        m.put("description", c.getDescription());
        m.put("image", c.getImageUrl());
        m.put("sort", c.getSortOrder());
        m.put("kind", "manual");
        m.put("productIds", productIdsOf(c.getId()));
        return m;
    }
}
