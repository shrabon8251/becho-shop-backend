package com.becho.saas.modules.tenancy.repository;

import com.becho.saas.modules.tenancy.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
  Optional<Store> findBySubdomain(String subdomain);
  Optional<Store> findByDomain(String domain);
  List<Store> findByTrialEndsAtBeforeAndStatusNot(java.time.LocalDateTime now, String status);
}
