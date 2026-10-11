package com.becho.saas.modules.billing.service;

import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Trial lifecycle: daily scan marks expired trials.
 * Stores with trial_ends_at in the past → status='expired'.
 * Login also enforces this lazily (AuthService).
 */
@Service
public class TrialExpiryService {

  private final StoreRepository stores;

  public TrialExpiryService(StoreRepository stores) {
    this.stores = stores;
  }

  /** Daily at 01:00: expire trials that ended. */
  @Scheduled(cron = "0 0 1 * * *")
  @Transactional
  public void expireTrials() {
    var expired = stores.findByTrialEndsAtBeforeAndStatusNot(
        LocalDateTime.now(), "expired");
    for (var store : expired) {
      store.setStatus("expired");
      stores.save(store);
    }
  }
}
