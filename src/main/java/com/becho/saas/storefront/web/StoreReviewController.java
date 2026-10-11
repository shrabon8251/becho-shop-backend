package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.catalog.entity.Product;
import com.becho.saas.modules.catalog.entity.Review;
import com.becho.saas.modules.catalog.repository.ProductRepository;
import com.becho.saas.modules.catalog.repository.ReviewRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Review endpoints: GET/POST /stores/{storeId}/reviews,
 * PUT/DELETE /stores/{storeId}/reviews/{id}.
 * Frontend Review: { id, productId, name, stars, text, createdAt }.
 */
@RestController
@RequestMapping("/stores/{storeId}/reviews")
public class StoreReviewController {

  private final ReviewRepository reviews;
  private final ProductRepository products;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreReviewController(ReviewRepository reviews, ProductRepository products,
                                StoreRepository stores, StoreScope scope) {
    this.reviews = reviews;
    this.products = products;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return reviews.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    Review r = new Review();
    r.setStore(store);
    r.setProduct(resolveProduct(sid, body.get("productId")));
    applyBody(r, body);
    return toFrontend(reviews.save(r));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Review r = reviews.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Review"));
    if (body.get("productId") != null) {
      r.setProduct(resolveProduct(sid, body.get("productId")));
    }
    applyBody(r, body);
    return toFrontend(reviews.save(r));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Review r = reviews.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Review"));
    reviews.delete(r);
  }

  private Product resolveProduct(Long sid, Object rawProductId) {
    if (rawProductId == null) {
      throw ApiException.badRequest("BAD_PRODUCT", "productId is required");
    }
    Long pid;
    try {
      pid = Long.valueOf(String.valueOf(rawProductId));
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("BAD_PRODUCT", "Invalid productId");
    }
    return products.findByIdAndStoreIdAndDeletedAtIsNull(pid, sid)
        .orElseThrow(() -> ApiException.notFound("Product"));
  }

  private void applyBody(Review r, Map<String, Object> body) {
    if (body.get("name") != null) {
      String name = String.valueOf(body.get("name")).trim();
      r.setName(name.isEmpty() ? "Anonymous" : name);
    }
    if (body.get("stars") instanceof Number n) {
      int stars = n.intValue();
      if (stars < 1 || stars > 5) {
        throw ApiException.badRequest("BAD_STARS", "Stars must be 1-5");
      }
      r.setStars(stars);
    }
    if (body.get("text") != null) r.setText(String.valueOf(body.get("text")));
  }

  private Map<String, Object> toFrontend(Review r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId().toString());
    m.put("productId", r.getProduct().getId().toString());
    m.put("name", r.getName());
    m.put("stars", r.getStars());
    m.put("text", r.getText());
    m.put("createdAt", r.getCreatedAt().atZone(ZoneOffset.UTC).toInstant().toEpochMilli());
    return m;
  }
}
