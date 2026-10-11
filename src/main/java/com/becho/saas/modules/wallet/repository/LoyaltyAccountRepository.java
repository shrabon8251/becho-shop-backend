package com.becho.saas.modules.wallet.repository;

import com.becho.saas.modules.wallet.entity.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {
  Optional<LoyaltyAccount> findByStoreIdAndCustomerId(Long storeId, Long customerId);
}
