package com.becho.saas.modules.shipping.repository;

import com.becho.saas.modules.shipping.entity.ShippingRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Store-scoped rate lookup — the price is always read server-side, never trusted from the client. */
public interface ShippingRateRepository extends JpaRepository<ShippingRate, Long> {
  Optional<ShippingRate> findByIdAndZoneStoreId(Long id, Long storeId);
}
