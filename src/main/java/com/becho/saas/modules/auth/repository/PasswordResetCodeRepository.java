package com.becho.saas.modules.auth.repository;

import com.becho.saas.modules.auth.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

  /** Latest unused code for the user (we invalidate older ones on each send). */
  Optional<PasswordResetCode> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);

  List<PasswordResetCode> findByUserIdAndUsedAtIsNull(Long userId);

  long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

  /** Rate limit by email even when no user exists (enumeration-safe). */
  long countByUserEmailAndCreatedAtAfter(String email, LocalDateTime since);
}
