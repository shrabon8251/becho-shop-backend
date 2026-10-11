package com.becho.saas.modules.orders.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * Generic per-store sequences. PK = (store_id, seq_name).
 * Matches the V1 store_sequences table: next_val holds the next value to hand out.
 */
@Entity
@Table(name = "store_sequences")
@IdClass(StoreSequence.Pk.class)
public class StoreSequence {

  @Id
  @Column(name = "store_id")
  private Long storeId;

  @Id
  @Column(name = "seq_name", length = 64)
  private String seqName;

  @Column(name = "next_val", nullable = false)
  private long nextVal = 1001;

  public Long getStoreId() { return storeId; }
  public void setStoreId(Long v) { this.storeId = v; }
  public String getSeqName() { return seqName; }
  public void setSeqName(String v) { this.seqName = v; }
  public long getNextVal() { return nextVal; }
  public void setNextVal(long v) { this.nextVal = v; }

  /** Hands out the current next value and increments. Call with a locked read inside a transaction. */
  public long takeNext() { return nextVal++; }

  public static class Pk implements Serializable {
    private Long storeId;
    private String seqName;
    public Pk() {}
    public Pk(Long storeId, String seqName) { this.storeId = storeId; this.seqName = seqName; }
    @Override public boolean equals(Object o) {
      if (!(o instanceof Pk p)) return false;
      return Objects.equals(storeId, p.storeId) && Objects.equals(seqName, p.seqName);
    }
    @Override public int hashCode() { return Objects.hash(storeId, seqName); }
  }
}
