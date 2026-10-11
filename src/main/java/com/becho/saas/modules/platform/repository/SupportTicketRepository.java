package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

  List<SupportTicket> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  Optional<SupportTicket> findByIdAndStoreId(Long id, Long storeId);
}
