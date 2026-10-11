package com.becho.saas.modules.wallet.repository;

import com.becho.saas.modules.wallet.entity.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {
  Optional<GiftCard> findByStoreIdAndCodeHashAndStatus(Long storeId, String codeHash, String status);
  List<GiftCard> findByStoreIdOrderByCreatedAtDesc(Long storeId);
  /** Pessimistic lock for redemption — prevents double-spend on concurrent orders. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from GiftCard g where g.store.id = :storeId and g.codeHash = :codeHash")
  Optional<GiftCard> findByStoreIdAndCodeHashForUpdate(@Param("storeId") Long storeId,
                                                       @Param("codeHash") String codeHash);

}
