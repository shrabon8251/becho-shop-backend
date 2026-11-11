package com.becho.saas.modules.analytics.entity;

import com.becho.saas.common.BaseEntity;
import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * One row per (store, day, visitor). A visitor is identified only by a
 * SHA-256 hash of (IP + User-Agent + day) — the raw IP and User-Agent are
 * never stored, so no shopper PII lives in this table. The unique
 * constraint makes repeat visits on the same day a no-op.
 */
@Entity
@Table(name = "store_visits",
        uniqueConstraints = @UniqueConstraint(name = "uq_store_visits_day_visitor",
                columnNames = {"store_id", "visited_date", "visitor_hash"}),
        indexes = @Index(name = "idx_store_visits_store_date",
                columnList = "store_id,visited_date"))
public class StoreVisit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "visited_date", nullable = false)
    private LocalDate visitedDate;

    /** SHA-256 hex of (client IP + "|" + User-Agent + "|" + date). */
    @Column(name = "visitor_hash", nullable = false, length = 64)
    private String visitorHash;

    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }
    public LocalDate getVisitedDate() { return visitedDate; }
    public void setVisitedDate(LocalDate visitedDate) { this.visitedDate = visitedDate; }
    public String getVisitorHash() { return visitorHash; }
    public void setVisitorHash(String visitorHash) { this.visitorHash = visitorHash; }
}
