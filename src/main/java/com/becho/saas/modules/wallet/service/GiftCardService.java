package com.becho.saas.modules.wallet.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.Hashing;
import com.becho.saas.modules.wallet.entity.GiftCard;
import com.becho.saas.modules.wallet.repository.GiftCardRepository;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Gift card redemption for checkout.
 * Uses pessimistic locking to prevent double-spend when two orders
 * try to use the same card simultaneously.
 */
@Service
public class GiftCardService {

  private final GiftCardRepository giftCards;

  public GiftCardService(GiftCardRepository giftCards) {
    this.giftCards = giftCards;
  }

  public record Redemption(long amountMinor, long remainingBalance) {}

  /**
   * Redeems up to {@code maxMinor} from the gift card.
   * Call exactly once when the order is placed, in the same transaction
   * as order creation. Throws if the card is invalid/expired/empty.
   */
  @Transactional
  public Redemption redeem(String code, long maxMinor) {
    Long storeId = TenantContext.requireStoreId();
    String normalized = code.trim().toUpperCase();
    // Pessimistic lock: prevents concurrent redemptions from overspending.
    GiftCard gc = giftCards.findByStoreIdAndCodeHashForUpdate(storeId, Hashing.sha256Hex(normalized))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GIFT_CARD_INVALID",
            "Gift card not found"));
    if (!"active".equals(gc.getStatus())) {
      throw new ApiException(HttpStatus.NOT_FOUND, "GIFT_CARD_INVALID", "Gift card not found");
    }
    if (gc.getExpiresAt() != null && LocalDateTime.now().isAfter(gc.getExpiresAt())) {
      throw new ApiException(HttpStatus.NOT_FOUND, "GIFT_CARD_INVALID", "Gift card not found");
    }
    if (gc.getBalanceMinor() <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "GIFT_CARD_EMPTY",
          "Gift card has no remaining balance");
    }
    long amount = Math.min(gc.getBalanceMinor(), maxMinor);
    gc.setBalanceMinor(gc.getBalanceMinor() - amount);
    giftCards.save(gc);
    return new Redemption(amount, gc.getBalanceMinor());
  }

  /**
   * Refunds a previous redemption (e.g. when the order is cancelled).
   * Adds the amount back to the card balance.
   */
  @Transactional
  public void refund(String codeHash, long amountMinor) {
    Long storeId = TenantContext.requireStoreId();
    giftCards.findByStoreIdAndCodeHashForUpdate(storeId, codeHash)
        .ifPresent(gc -> {
          gc.setBalanceMinor(gc.getBalanceMinor() + amountMinor);
          giftCards.save(gc);
        });
  }
}
