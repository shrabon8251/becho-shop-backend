package com.becho.saas.modules.inventory.repository;

import com.becho.saas.modules.inventory.entity.InventoryLevel;
import com.becho.saas.modules.inventory.entity.InventoryLevelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, InventoryLevelId> {

    // BUG-29 fix: removed @Lock(PESSIMISTIC_WRITE) — Hibernate generated
    // "FOR UPDATE OF il1_0" which TiDB/MySQL does not support (it parsed the
    // alias as a table name -> "Table 'bechodb.il1_0' doesn't exist").
    // Plain select works for stock adjustments; oversell protection for
    // concurrent orders can be re-added with a TiDB-compatible native query.
    @Query("SELECT l FROM InventoryLevel l WHERE l.location.id = :locationId AND l.variant.id = :variantId")
    Optional<InventoryLevel> findForUpdate(Long locationId, Long variantId);

    /** BUG-29 fix: on-hand stock per variant (summed across the store's locations). */
    @Query("SELECT l.variant.id, COALESCE(SUM(l.onHand), 0) FROM InventoryLevel l " +
            "WHERE l.variant.id IN :variantIds GROUP BY l.variant.id")
    List<Object[]> sumOnHandByVariantIds(@Param("variantIds") List<Long> variantIds);
}
