package com.becho.saas.modules.billing.repository;

import com.becho.saas.modules.billing.entity.PromoCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, String> {

  /**
   * Lock the promo row for the whole transaction: check-then-increment is
   * atomic, so max_uses can never be overshot by concurrent redemptions.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PromoCode p where p.code = :code")
  Optional<PromoCode> findByCodeForUpdate(String code);

  Optional<PromoCode> findByCode(String code);
}
