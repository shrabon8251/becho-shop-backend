package com.becho.saas.modules.inventory.repository;

import com.becho.saas.modules.inventory.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {
  List<Location> findByStoreIdAndDeletedAtIsNull(Long storeId);
  Optional<Location> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);
}
