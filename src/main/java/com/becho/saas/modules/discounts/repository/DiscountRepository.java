package com.becho.saas.modules.discounts.repository;

import com.becho.saas.modules.discounts.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {
    Optional<Discount> findByStoreIdAndCodeAndDeletedAtIsNull(Long storeId, String code);
    List<Discount> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long storeId);
    /** Pessimistic lock for consume — prevents usage-limit overrun on concurrent checkouts. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Discount d where d.store.id = :storeId and d.code = :code and d.deletedAt is null")
    Optional<Discount> findByStoreIdAndCodeForUpdate(@Param("storeId") Long storeId,
                                                     @Param("code") String code);
}
