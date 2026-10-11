package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.Page;
import com.becho.saas.modules.platform.repository.PageRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible blog/page endpoints: /stores/{storeId}/posts[/{id}].
 * Backed by the Page entity. Frontend Crud&lt;Post&gt;:
 * { id, type: "post"|"page", title, slug, body, published, createdAt }.
 * Note: the storefront must only receive published posts — use ?published=true
 * or filter client-side; this admin endpoint returns all non-deleted pages.
 */
@RestController
@RequestMapping("/stores/{storeId}/posts")
public class StorePostController {

  private final PageRepository pages;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StorePostController(PageRepository pages, StoreRepository stores, StoreScope scope) {
    this.pages = pages;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId,
                                        @RequestParam(required = false) Boolean published) {
    Long sid = scope.require(storeId);
    return pages.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(sid).stream()
        .filter(p -> published == null || published == "published".equals(p.getStatus()))
        .map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Page p = new Page();
    p.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    applyBody(p, body);
    return toFrontend(pages.save(p));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                  @PathVariable String id,
                                  @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    if (StoreScope.isNewId(id)) return create(storeId, body);
    Page p = pages.findByIdAndStoreIdAndDeletedAtIsNull(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Post"));
    applyBody(p, body);
    return toFrontend(pages.save(p));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Page p = pages.findByIdAndStoreIdAndDeletedAtIsNull(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Post"));
    p.setDeletedAt(java.time.LocalDateTime.now());
    pages.save(p);
  }

  private void applyBody(Page p, Map<String, Object> body) {
    if (body.get("title") != null) p.setTitle((String) body.get("title"));
    if (body.get("slug") != null) {
      p.setSlug((String) body.get("slug"));
    } else if (p.getSlug() == null && body.get("title") != null) {
      p.setSlug(((String) body.get("title")).toLowerCase().trim()
          .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""));
    }
    if (body.get("body") != null) p.setBody((String) body.get("body"));
    Object published = body.get("published");
    if (published instanceof Boolean b) p.setStatus(b ? "published" : "draft");
  }

  private Map<String, Object> toFrontend(Page p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.getId().toString());
    m.put("type", "page");
    m.put("title", p.getTitle());
    m.put("slug", p.getSlug());
    m.put("body", p.getBody());
    m.put("published", "published".equals(p.getStatus()));
    m.put("createdAt", p.getCreatedAt() != null
        ? p.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli() : System.currentTimeMillis());
    return m;
  }
}
