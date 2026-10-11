package com.becho.saas.modules.platform.entity;

import com.becho.saas.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Landing page CMS content. Single row (id=1). The JSON is the
 * LandingContent shape from the frontend (announcement, hero, features,
 * pricing, themes, testimonials, faq, cta).
 */
@Entity
@Table(name = "platform_landing")
public class PlatformLanding extends BaseEntity {

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json", nullable = false)
  private String content;

  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
}
