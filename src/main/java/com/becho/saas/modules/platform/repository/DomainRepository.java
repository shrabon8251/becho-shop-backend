package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DomainRepository extends JpaRepository<Domain, Long> {

  List<Domain> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  Optional<Domain> findByIdAndStoreId(Long id, Long storeId);
}
