package com.becho.saas.modules.shipping.repository;

import com.becho.saas.modules.shipping.entity.ShippingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShippingProfileRepository extends JpaRepository<ShippingProfile, Long> {

  List<ShippingProfile> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  Optional<ShippingProfile> findByIdAndStoreId(Long id, Long storeId);
}
