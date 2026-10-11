package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
}
