package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, EmailTemplate.EmailTemplateId> {

  List<EmailTemplate> findByStoreId(Long storeId);
}
