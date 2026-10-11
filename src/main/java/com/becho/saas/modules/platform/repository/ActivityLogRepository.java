package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
  List<ActivityLog> findByStoreIdOrderByCreatedAtDescIdDesc(Long storeId,
      org.springframework.data.domain.Limit limit);
}
