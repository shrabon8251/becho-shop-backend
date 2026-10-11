package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {
  List<Webhook> findByStoreIdAndTopicAndStatus(Long storeId, String topic, String status);
  List<Webhook> findByStoreIdOrderByCreatedAtDesc(Long storeId);
}
