package com.becho.saas.modules.subscriptions.repository;

import com.becho.saas.modules.subscriptions.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
  List<Subscription> findByStatusAndNextBillingAtBefore(String status, LocalDateTime before);
  List<Subscription> findByStoreIdAndCustomerId(Long storeId, Long customerId);
}
