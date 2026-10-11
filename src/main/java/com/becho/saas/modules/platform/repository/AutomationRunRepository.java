package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.AutomationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

  /** Due runs: pending and scheduled_for <= now, oldest first. */
  @Query("SELECT r FROM AutomationRun r WHERE r.status = 'pending' "
      + "AND r.scheduledFor <= :now ORDER BY r.scheduledFor ASC")
  List<AutomationRun> findDue(LocalDateTime now);

  /** Has this automation already been queued for this email + trigger? (dedupe) */
  boolean existsByAutomationIdAndRecipientEmailAndTriggerAndStatus(
      Long automationId, String email, String trigger, String status);

  /**
   * Winback candidates: customers of a store with no non-cancelled order
   * since the cutoff (or never ordered, created before cutoff).
   */
  @Query("""
      SELECT c.id FROM Customer c WHERE c.store.id = :storeId
      AND c.deletedAt IS NULL AND c.email IS NOT NULL
      AND c.createdAt < :cutoff
      AND NOT EXISTS (SELECT 1 FROM Order o WHERE o.customer.id = c.id
        AND o.cancelledAt IS NULL AND o.createdAt >= :cutoff)
      """)
  List<Long> findWinbackCandidates(Long storeId, LocalDateTime cutoff);
}
