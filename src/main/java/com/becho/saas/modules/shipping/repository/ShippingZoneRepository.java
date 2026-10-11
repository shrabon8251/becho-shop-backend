package com.becho.saas.modules.shipping.repository;

import com.becho.saas.modules.shipping.entity.ShippingZone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Long> {
  List<ShippingZone> findByStoreId(Long storeId);
}
