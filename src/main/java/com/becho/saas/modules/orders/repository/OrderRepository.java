package com.becho.saas.modules.orders.repository;

import com.becho.saas.modules.orders.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

    @Query("""
      SELECT o FROM Order o LEFT JOIN FETCH o.lines
      WHERE o.id = :id AND o.store.id = :storeId AND o.deletedAt IS NULL
      """)
    Optional<Order> findWithLines(Long id, Long storeId);

    @Query("""
      SELECT o FROM Order o LEFT JOIN FETCH o.customer
      WHERE o.store.id = :storeId AND o.deletedAt IS NULL
        AND (:financial IS NULL OR o.financialStatus = :financial)
        AND (:cursorAt IS NULL OR o.createdAt < :cursorAt
             OR (o.createdAt = :cursorAt AND o.id < :cursorId))
      ORDER BY o.createdAt DESC, o.id DESC
      """)
    List<Order> findPage(Long storeId, String financial,
                         LocalDateTime cursorAt, Long cursorId,
                         org.springframework.data.domain.Limit limit);

    /**
     * Admin Orders page — one server-side page. Filters: tab (unf = unfulfilled and not
     * cancelled, unp = pending payment, ful = fulfilled), pay/ful statuses (lowercase DB
     * values), createdAt range, q = order number OR customer name OR email OR phone.
     * Sort comes from the Pageable (allowlist switch in the controller).
     */
    @Query(value = """
      SELECT o FROM Order o LEFT JOIN FETCH o.customer c
      WHERE o.store.id = :storeId AND o.deletedAt IS NULL
        AND (:since IS NULL OR o.createdAt >= :since)
        AND (:tab IS NULL
             OR (:tab = 'unf' AND o.fulfillmentStatus = 'unfulfilled' AND o.financialStatus <> 'cancelled')
             OR (:tab = 'unp' AND o.financialStatus = 'pending')
             OR (:tab = 'ful' AND o.fulfillmentStatus = 'fulfilled'))
        AND (:pay IS NULL OR o.financialStatus = :pay)
        AND (:ful IS NULL OR o.fulfillmentStatus = :ful)
        AND (:q IS NULL
             OR (:qNum IS NOT NULL AND o.orderNumber = :qNum)
             OR LOWER(CONCAT(COALESCE(c.firstName, ''), ' ', COALESCE(c.lastName, ''))) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(o.email) LIKE LOWER(CONCAT('%', :q, '%'))
             OR o.phone LIKE CONCAT('%', :q, '%'))
      """,
            countQuery = """
      SELECT COUNT(o) FROM Order o LEFT JOIN o.customer c
      WHERE o.store.id = :storeId AND o.deletedAt IS NULL
        AND (:since IS NULL OR o.createdAt >= :since)
        AND (:tab IS NULL
             OR (:tab = 'unf' AND o.fulfillmentStatus = 'unfulfilled' AND o.financialStatus <> 'cancelled')
             OR (:tab = 'unp' AND o.financialStatus = 'pending')
             OR (:tab = 'ful' AND o.fulfillmentStatus = 'fulfilled'))
        AND (:pay IS NULL OR o.financialStatus = :pay)
        AND (:ful IS NULL OR o.fulfillmentStatus = :ful)
        AND (:q IS NULL
             OR (:qNum IS NOT NULL AND o.orderNumber = :qNum)
             OR LOWER(CONCAT(COALESCE(c.firstName, ''), ' ', COALESCE(c.lastName, ''))) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(o.email) LIKE LOWER(CONCAT('%', :q, '%'))
             OR o.phone LIKE CONCAT('%', :q, '%'))
      """)
    Page<Order> findAdminPage(Long storeId, String tab, String pay, String ful,
                              LocalDateTime since, String q, Long qNum, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.store.id = :storeId AND o.deletedAt IS NULL AND (:since IS NULL OR o.createdAt >= :since)")
    long countTotal(Long storeId, LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.store.id = :storeId AND o.deletedAt IS NULL AND (:since IS NULL OR o.createdAt >= :since) AND o.fulfillmentStatus = 'unfulfilled' AND o.financialStatus <> 'cancelled'")
    long countUnfulfilled(Long storeId, LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.store.id = :storeId AND o.deletedAt IS NULL AND (:since IS NULL OR o.createdAt >= :since) AND o.financialStatus = 'pending'")
    long countUnpaid(Long storeId, LocalDateTime since);

    @Query("SELECT COALESCE(SUM(o.totalMinor), 0L) FROM Order o WHERE o.store.id = :storeId AND o.deletedAt IS NULL AND (:since IS NULL OR o.createdAt >= :since) AND o.financialStatus = 'paid'")
    long sumPaidTotal(Long storeId, LocalDateTime since);

    /** Converted orders in a window: paid or refunded (a real purchase either way). Batch B — used by StoreAnalyticsController. */
    @Query("""
      SELECT COUNT(o) FROM Order o
      WHERE o.store.id = :storeId AND o.deletedAt IS NULL
        AND o.financialStatus IN ('paid', 'refunded')
        AND o.createdAt BETWEEN :from AND :to
      """)
    long countConvertedOrders(Long storeId, LocalDateTime from, LocalDateTime to);
}
