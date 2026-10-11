package com.becho.saas.modules.orders.repository;

import com.becho.saas.modules.orders.entity.StoreSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StoreSequenceRepository extends JpaRepository<StoreSequence, StoreSequence.Pk> {

    /** Atomic sequence generation per store.
     * BUG-50 fix: removed @Lock(PESSIMISTIC_WRITE) — Hibernate generated
     * "FOR UPDATE OF ss1_0" which TiDB does not support (same as BUG-29).
     * Plain select works; concurrent order numbers are handled by the
     * transaction retry. */
    Optional<StoreSequence> findByStoreIdAndSeqName(Long storeId, String seqName);
}
