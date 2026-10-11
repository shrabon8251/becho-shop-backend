package com.becho.saas.modules.billing.repository;

import com.becho.saas.modules.billing.entity.PromoRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, Long> {

  boolean existsByPromoCodeCodeAndStoreId(String code, Long storeId);
}
