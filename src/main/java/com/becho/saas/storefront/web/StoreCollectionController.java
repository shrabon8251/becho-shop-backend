package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.catalog.entity.Collection;
import com.becho.saas.modules.catalog.repository.CollectionRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
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

  public StoreCollectionController(CollectionRepository collections,
                                    StoreRepository storeRepository, StoreScope scope) {
    this.collections = collections;
    this.storeRepository = storeRepository;
    this.scope = scope;
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
    return toFrontend(collections.save(c));
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
    return toFrontend(collections.save(c));
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

  private Map<String, Object> toFrontend(Collection c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId().toString());
    m.put("name", c.getTitle());
    m.put("description", c.getDescription());
    m.put("image", c.getImageUrl());
    m.put("sort", c.getSortOrder());
    m.put("kind", "manual");
    return m;
  }
}
