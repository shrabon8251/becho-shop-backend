package com.becho.saas.modules.shipping.repository;

import com.becho.saas.modules.shipping.entity.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {
  List<TaxRate> findByStoreIdAndCountry(Long storeId, String country);
}
