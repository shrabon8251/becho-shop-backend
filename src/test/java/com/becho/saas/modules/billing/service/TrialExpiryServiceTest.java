package com.becho.saas.modules.billing.service;

import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Trial expiry: stores past trial_ends_at get status='expired'.
 */
@ExtendWith(MockitoExtension.class)
class TrialExpiryServiceTest {

  @Mock StoreRepository stores;
  @InjectMocks TrialExpiryService service;

  @Test
  void expireTrials_marksExpired() {
    Store s = new Store();
    s.setStatus("active");
    s.setTrialEndsAt(LocalDateTime.now().minusDays(1));
    when(stores.findByTrialEndsAtBeforeAndStatusNot(any(), eq("expired")))
        .thenReturn(List.of(s));

    service.expireTrials();

    assert "expired".equals(s.getStatus());
    verify(stores).save(s);
  }

  @Test
  void expireTrials_nothingToExpire() {
    when(stores.findByTrialEndsAtBeforeAndStatusNot(any(), eq("expired")))
        .thenReturn(List.of());
    service.expireTrials();
    verify(stores, never()).save(any());
  }
}
