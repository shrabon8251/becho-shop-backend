package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.Redirect;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RedirectRepository extends JpaRepository<Redirect, Long> {
  List<Redirect> findByStoreIdOrderByCreatedAtDesc(Long storeId);
  Optional<Redirect> findByIdAndStoreId(Long id, Long storeId);
  Optional<Redirect> findByStoreIdAndFromPath(Long storeId, String fromPath);
}
