package com.becho.saas.modules.catalog.repository;

import com.becho.saas.modules.catalog.entity.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BundleRepository extends JpaRepository<Bundle, Long> {

  List<Bundle> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  Optional<Bundle> findByIdAndStoreId(Long id, Long storeId);
}
