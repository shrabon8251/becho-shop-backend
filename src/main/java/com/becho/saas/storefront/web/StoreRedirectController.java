package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.Redirect;
import com.becho.saas.modules.platform.repository.RedirectRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible redirect endpoints: /stores/{storeId}/redirects[/{id}].
 * DB-backed. Frontend Redirect { id, from, to }.
 */
@RestController
@RequestMapping("/stores/{storeId}/redirects")
public class StoreRedirectController {

  private final RedirectRepository redirects;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreRedirectController(RedirectRepository redirects, StoreRepository stores, StoreScope scope) {
    this.redirects = redirects;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return redirects.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Redirect r = new Redirect();
    r.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    applyBody(r, body);
    return toFrontend(redirects.save(r));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Redirect r;
    if (StoreScope.isNewId(id)) {
      r = new Redirect();
      r.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    } else {
      r = redirects.findByIdAndStoreId(StoreScope.lid(id), sid)
          .orElseThrow(() -> ApiException.notFound("Redirect"));
    }
    applyBody(r, body);
    return toFrontend(redirects.save(r));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Redirect r = redirects.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Redirect"));
    redirects.delete(r);
  }

  private void applyBody(Redirect r, Map<String, Object> body) {
    if (body.get("from") != null) r.setFromPath(String.valueOf(body.get("from")));
    else if (body.get("fromPath") != null) r.setFromPath(String.valueOf(body.get("fromPath")));
    if (body.get("to") != null) r.setToPath(String.valueOf(body.get("to")));
    else if (body.get("toPath") != null) r.setToPath(String.valueOf(body.get("toPath")));
    if (body.get("statusCode") instanceof Number n) {
      int code = n.intValue();
      r.setStatusCode(code == 302 ? 302 : 301);
    }
  }

  private Map<String, Object> toFrontend(Redirect r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId().toString());
    m.put("from", r.getFromPath());
    m.put("to", r.getToPath());
    return m;
  }
}
