package com.becho.saas.modules.platform.entity;

import com.becho.saas.modules.tenancy.entity.Store;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Per-store email template (order_confirmation, order_shipped, order_refunded).
 * Keyed by (store_id, template key).
 */
@Entity
@Table(name = "email_templates")
@IdClass(EmailTemplate.EmailTemplateId.class)
public class EmailTemplate {

  @Id
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Id
  @Column(name = "`key`", nullable = false, length = 64)
  private String key;

  @Column(nullable = false, length = 255)
  private String subject;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = LocalDateTime.now();
  }

  public Store getStore() { return store; }
  public void setStore(Store store) { this.store = store; }
  public String getKey() { return key; }
  public void setKey(String key) { this.key = key; }
  public String getSubject() { return subject; }
  public void setSubject(String subject) { this.subject = subject; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }

  public static class EmailTemplateId implements Serializable {
    private Long store;
    private String key;

    public EmailTemplateId() {}
    public EmailTemplateId(Long store, String key) { this.store = store; this.key = key; }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof EmailTemplateId that)) return false;
      return Objects.equals(store, that.store) && Objects.equals(key, that.key);
    }
    @Override public int hashCode() { return Objects.hash(store, key); }
  }
}
