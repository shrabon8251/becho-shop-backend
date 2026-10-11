package com.becho.saas.modules.catalog.repository;

import com.becho.saas.modules.catalog.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CollectionRepository extends JpaRepository<Collection, Long> {
  List<Collection> findByStoreIdAndDeletedAtIsNullOrderByTitle(Long storeId);
  Optional<Collection> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);
}
