package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Contact-form message from the marketing site. Stored so the team can
 * follow up from an admin view later; a notification email is also attempted.
 */
@Entity
@Table(name = "contact_messages",
    indexes = @Index(name = "idx_contact_created", columnList = "created_at"))
public class ContactMessage extends BaseEntity {

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(length = 255)
  private String subject;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String message;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getSubject() { return subject; }
  public void setSubject(String subject) { this.subject = subject; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
}
