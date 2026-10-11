package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {
  List<Page> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long storeId);
  Optional<Page> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);
}
