package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.Automation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AutomationRepository extends JpaRepository<Automation, Long> {

  List<Automation> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  List<Automation> findByStoreIdAndTriggerAndActiveTrue(Long storeId, String trigger);

  Optional<Automation> findByIdAndStoreId(Long id, Long storeId);
}
