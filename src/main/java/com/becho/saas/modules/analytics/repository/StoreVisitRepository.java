package com.becho.saas.modules.analytics.repository;

import com.becho.saas.modules.analytics.entity.StoreVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;

public interface StoreVisitRepository extends JpaRepository<StoreVisit, Long> {

    boolean existsByStoreIdAndVisitedDateAndVisitorHash(Long storeId, LocalDate visitedDate, String visitorHash);

    /** Unique visitors in a date range (inclusive). */
    @Query("""
      SELECT COUNT(DISTINCT v.visitorHash) FROM StoreVisit v
      WHERE v.store.id = :storeId
        AND v.visitedDate BETWEEN :from AND :to
      """)
    long countVisitors(Long storeId, LocalDate from, LocalDate to);
}
