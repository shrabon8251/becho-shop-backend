package com.becho.saas.modules.auth.repository;

import com.becho.saas.modules.auth.entity.PhoneVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhoneVerificationCodeRepository extends JpaRepository<PhoneVerificationCode, Long> {

  Optional<PhoneVerificationCode> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);

  List<PhoneVerificationCode> findByUserIdAndUsedAtIsNull(Long userId);

  long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
