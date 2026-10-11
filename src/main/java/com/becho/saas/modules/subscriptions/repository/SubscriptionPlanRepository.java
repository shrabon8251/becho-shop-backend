package com.becho.saas.modules.subscriptions.repository;

import com.becho.saas.modules.subscriptions.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
  List<SubscriptionPlan> findByStoreIdOrderByCreatedAtDesc(Long storeId);
  Optional<SubscriptionPlan> findByIdAndStoreId(Long id, Long storeId);
}
