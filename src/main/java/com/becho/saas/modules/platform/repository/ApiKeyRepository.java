package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
  Optional<ApiKey> findByKeyHash(String keyHash);
  java.util.List<ApiKey> findByStoreIdOrderByCreatedAtDesc(Long storeId);
}
