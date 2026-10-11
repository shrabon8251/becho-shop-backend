package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.Domain;
import com.becho.saas.modules.platform.repository.DomainRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain endpoints: GET/POST /stores/{storeId}/domains,
 * PUT/DELETE /stores/{storeId}/domains/{id},
 * POST /stores/{storeId}/domains/{id}/primary.
 * Frontend: { id, domain, primary, status: "Pending"|"Connected"|"Error" }.
 */
@RestController
@RequestMapping("/stores/{storeId}/domains")
public class StoreDomainController {

  private static final Set<String> STATUSES = Set.of("Pending", "Connected", "Error");

  private final DomainRepository domains;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreDomainController(DomainRepository domains,
                                StoreRepository stores, StoreScope scope) {
    this.domains = domains;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return domains.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    Object raw = body.get("domain");
    if (raw == null || String.valueOf(raw).isBlank()) {
      throw ApiException.badRequest("BAD_DOMAIN", "Domain is required");
    }
    String name = String.valueOf(raw).trim().toLowerCase();
    if (!name.matches("[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?)+")) {
      throw ApiException.badRequest("BAD_DOMAIN", "Invalid domain format");
    }
    Domain d = new Domain();
    d.setStore(store);
    d.setDomain(name);
    d.setPrimary(Boolean.TRUE.equals(body.get("primary")));
    Object st = body.get("status");
    d.setStatus(st != null && STATUSES.contains(String.valueOf(st))
        ? String.valueOf(st) : "Pending");
    try {
      return toFrontend(domains.save(d));
    } catch (DataIntegrityViolationException e) {
      throw ApiException.badRequest("DOMAIN_TAKEN", "Domain is already in use");
    }
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Domain d = domains.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Domain"));
    if (body.get("status") != null) {
      String st = String.valueOf(body.get("status"));
      if (!STATUSES.contains(st)) {
        throw ApiException.badRequest("BAD_STATUS", "Unknown status: " + st);
      }
      d.setStatus(st);
    }
    return toFrontend(domains.save(d));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Domain d = domains.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Domain"));
    domains.delete(d);
  }

  @PostMapping("/{id}/primary")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  @Transactional
  public Map<String, Object> setPrimary(@PathVariable String storeId,
                                        @PathVariable String id) {
    Long sid = scope.require(storeId);
    Domain target = domains.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Domain"));
    for (Domain d : domains.findByStoreIdOrderByCreatedAtDesc(sid)) {
      if (d.isPrimary()) {
        d.setPrimary(false);
        domains.save(d);
      }
    }
    target.setPrimary(true);
    return toFrontend(domains.save(target));
  }

  private Map<String, Object> toFrontend(Domain d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", d.getId().toString());
    m.put("domain", d.getDomain());
    m.put("primary", d.isPrimary());
    m.put("status", d.getStatus());
    return m;
  }
}
