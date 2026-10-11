package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.inventory.entity.Location;
import com.becho.saas.modules.inventory.repository.LocationRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible location endpoints: /stores/{storeId}/locations[/{id}].
 */
@RestController
@RequestMapping("/stores/{storeId}/locations")
public class StoreLocationController {

  private final LocationRepository locations;
  private final StoreRepository storeRepository;
  private final StoreScope scope;

  public StoreLocationController(LocationRepository locations,
                                  StoreRepository storeRepository, StoreScope scope) {
    this.locations = locations;
    this.storeRepository = storeRepository;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return locations.findByStoreIdAndDeletedAtIsNull(sid)
        .stream().map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Location loc = new Location();
    loc.setStore(storeRepository.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    applyBody(loc, body, sid);
    return toFrontend(locations.save(loc));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    if (StoreScope.isNewId(id)) return create(storeId, body);
    Location loc = locations.findByIdAndStoreIdAndDeletedAtIsNull(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Location"));
    applyBody(loc, body, sid);
    return toFrontend(locations.save(loc));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Location loc = locations.findByIdAndStoreIdAndDeletedAtIsNull(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Location"));
    loc.setDeletedAt(java.time.LocalDateTime.now());
    locations.save(loc);
  }

  private void applyBody(Location loc, Map<String, Object> body, Long sid) {
    if (body.get("name") != null) loc.setName((String) body.get("name"));
    if (body.get("address") != null) loc.setAddress((String) body.get("address"));
    Object isDefault = body.get("isDefault");
    if (isDefault instanceof Boolean b && b) {
      locations.findByStoreIdAndDeletedAtIsNull(sid).forEach(l -> {
        if (l.isDefaultLocation() && (loc.getId() == null || !l.getId().equals(loc.getId()))) {
          l.setDefaultLocation(false);
          locations.save(l);
        }
      });
      loc.setDefaultLocation(true);
    }
  }

  private Map<String, Object> toFrontend(Location l) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", l.getId().toString());
    m.put("name", l.getName());
    m.put("address", l.getAddress());
    m.put("isDefault", l.isDefaultLocation());
    m.put("status", l.getStatus());
    return m;
  }
}
