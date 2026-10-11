package com.becho.saas.storefront.web;

import com.becho.saas.modules.platform.entity.ActivityLog;
import com.becho.saas.modules.platform.repository.ActivityLogRepository;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible activity endpoints: GET /stores/{storeId}/activity, POST /stores/{storeId}/activity.
 * Uses the real ActivityLog table for reads; writes go through the repository directly
 * (synchronous, so the entry is visible immediately).
 */
@RestController
@RequestMapping("/stores/{storeId}/activity")
public class StoreActivityController {

  private final ActivityLogRepository activityLogs;
  private final StoreScope scope;

  public StoreActivityController(ActivityLogRepository activityLogs, StoreScope scope) {
    this.activityLogs = activityLogs;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return activityLogs.findByStoreIdOrderByCreatedAtDescIdDesc(sid, Limit.of(100))
        .stream().map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public void log(@PathVariable String storeId, @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    ActivityLog a = new ActivityLog();
    a.setStoreId(sid);
    a.setActorId(TenantContext.userId());
    a.setActorType("user");
    a.setAction(body.get("action") != null ? String.valueOf(body.get("action")) : "unknown");
    if (body.get("actor") != null) a.setActorType(String.valueOf(body.get("actor")));
    if (body.get("detail") != null) a.setChanges(String.valueOf(body.get("detail")));
    activityLogs.save(a);
  }

  private Map<String, Object> toFrontend(ActivityLog a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId().toString());
    m.put("actor", a.getActorType());
    m.put("action", a.getAction());
    m.put("detail", a.getChanges());
    m.put("entityType", a.getEntityType());
    m.put("at", a.getCreatedAt() != null
        ? a.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli() : null);
    return m;
  }
}
