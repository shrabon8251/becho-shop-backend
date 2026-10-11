package com.becho.saas.modules.wallet.repository;

import com.becho.saas.modules.wallet.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayoutRepository extends JpaRepository<Payout, Long> {
  List<Payout> findByStoreIdOrderByCreatedAtDesc(Long storeId);
}
