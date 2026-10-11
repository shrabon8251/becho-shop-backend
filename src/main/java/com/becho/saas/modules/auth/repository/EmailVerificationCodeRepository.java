package com.becho.saas.modules.auth.repository;

import com.becho.saas.modules.auth.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

  /** Latest unused code for the user (we invalidate older ones on each send). */
  Optional<EmailVerificationCode> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);

  List<EmailVerificationCode> findByUserIdAndUsedAtIsNull(Long userId);

  long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
