package com.becho.saas.modules.orders.repository;

import com.becho.saas.modules.orders.entity.StoreSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface StoreSequenceRepository extends JpaRepository<StoreSequence, StoreSequence.Pk> {

  /** Atomic sequence generation per store. Row is locked for the transaction. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<StoreSequence> findByStoreIdAndSeqName(Long storeId, String seqName);
}
