package com.becho.saas.modules.inventory.repository;

import com.becho.saas.modules.inventory.entity.InventoryLevel;
import com.becho.saas.modules.inventory.entity.InventoryLevelId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, InventoryLevelId> {

    /** Pessimistic lock — concurrent order placements cannot oversell. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM InventoryLevel l WHERE l.location.id = :locationId AND l.variant.id = :variantId")
    Optional<InventoryLevel> findForUpdate(Long locationId, Long variantId);

    /** BUG-29 fix: on-hand stock per variant (summed across the store's locations). */
    @Query("SELECT l.variant.id, COALESCE(SUM(l.onHand), 0) FROM InventoryLevel l " +
            "WHERE l.variant.id IN :variantIds GROUP BY l.variant.id")
    List<Object[]> sumOnHandByVariantIds(@Param("variantIds") List<Long> variantIds);
}
