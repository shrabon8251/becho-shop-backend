package com.becho.saas.modules.catalog.repository;

import com.becho.saas.modules.catalog.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

  List<Review> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  Optional<Review> findByIdAndStoreId(Long id, Long storeId);
}
