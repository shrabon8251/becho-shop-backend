package com.becho.saas.modules.auth.repository;

import com.becho.saas.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  Optional<RefreshToken> findByTokenHash(String tokenHash);
  java.util.List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}
