package com.becho.saas.modules.marketing.repository;

import com.becho.saas.modules.marketing.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
  List<Campaign> findByStoreIdOrderByCreatedAtDesc(Long storeId);
  Optional<Campaign> findByIdAndStoreId(Long id, Long storeId);
}
