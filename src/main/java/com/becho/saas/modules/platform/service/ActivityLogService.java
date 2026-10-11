package com.becho.saas.modules.platform.service;

import com.becho.saas.modules.platform.entity.ActivityLog;
import com.becho.saas.modules.platform.repository.ActivityLogRepository;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Append-only audit log. Fire-and-forget via @Async so it never
 * slows down the request path. No update/delete methods exist by design.
 *
 * Tenant context is captured on the CALLER thread, then handed to the
 * async writer — ThreadLocals don't cross threads.
 */
@Service
public class ActivityLogService {

  private final ActivityLogWriter writer;

  public ActivityLogService(ActivityLogWriter writer) {
    this.writer = writer;
  }

  public void log(String action, String entityType, Long entityId, String metadata, String ip) {
    Long storeId = TenantContext.storeId();
    Long actorId = TenantContext.userId();
    writer.writeAsync(storeId, actorId, action, entityType, entityId, metadata, ip);
  }

  @Component
  static class ActivityLogWriter {
    private final ActivityLogRepository repo;

    ActivityLogWriter(ActivityLogRepository repo) {
      this.repo = repo;
    }

    @Async
    public void writeAsync(Long storeId, Long actorId, String action, String entityType,
                           Long entityId, String metadata, String ip) {
      ActivityLog a = new ActivityLog();
      a.setStoreId(storeId);
      a.setActorId(actorId);
      a.setActorType("staff");
      a.setAction(action);
      a.setEntityType(entityType);
      a.setEntityId(entityId);
      a.setChanges(metadata);
      a.setIpAddress(ip);
      repo.save(a);
    }
  }
}
