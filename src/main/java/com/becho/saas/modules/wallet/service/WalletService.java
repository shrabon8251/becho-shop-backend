package com.becho.saas.modules.wallet.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.customers.entity.Customer;
import com.becho.saas.modules.customers.repository.CustomerRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.modules.wallet.entity.*;
import com.becho.saas.modules.wallet.repository.*;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Gift cards, loyalty points and store credit — one wallet aggregate.
 * Balances move only inside transactions; every mutation is auditable.
 */
@Service
public class WalletService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GiftCardRepository giftCards;
    private final LoyaltyAccountRepository loyalty;
    private final CustomerRepository customers;
    private final StoreRepository stores;

    public WalletService(GiftCardRepository giftCards, LoyaltyAccountRepository loyalty,
                         CustomerRepository customers, StoreRepository stores) {
        this.giftCards = giftCards;
        this.loyalty = loyalty;
        this.customers = customers;
        this.stores = stores;
    }

    /** Raw code is returned ONCE in the result — it is never persisted. */
    public record IssuedGiftCard(Long id, String code, long amountMinor, LocalDateTime expiresAt) {}

    @Transactional
    public IssuedGiftCard issueGiftCard(long amountMinor, LocalDateTime expiresAt) {
        Long storeId = TenantContext.requireStoreId();
        Store store = stores.findById(storeId).orElseThrow(() -> ApiException.notFound("Store"));
        String code = generateCode();
        GiftCard gc = new GiftCard();
        gc.setStore(store);
        gc.setCodeHash(sha256(code));
        gc.setCodePrefix(code.substring(0, 4));
        gc.setInitialMinor(amountMinor);
        gc.setBalanceMinor(amountMinor);
        gc.setExpiresAt(expiresAt);
        giftCards.save(gc);
        return new IssuedGiftCard(gc.getId(), code, amountMinor, expiresAt);
    }

    /** Redeem returns the amount actually covered (may be partial). */
    @Transactional
    public long redeemGiftCard(String code, long orderTotalMinor) {
        Long storeId = TenantContext.requireStoreId();
        // Pessimistic lock: prevents concurrent redemptions from double-spending
        // the same card (same pattern as GiftCardService.redeem).
        GiftCard gc = giftCards.findByStoreIdAndCodeHashForUpdate(storeId, sha256(code))
                .filter(g -> "active".equals(g.getStatus()))
                .orElseThrow(() -> ApiException.badRequest("GIFT_INVALID", "Gift card is invalid"));
        if (gc.getExpiresAt() != null && LocalDateTime.now().isAfter(gc.getExpiresAt())) {
            throw ApiException.badRequest("GIFT_EXPIRED", "Gift card expired");
        }
        long covered = Math.min(gc.getBalanceMinor(), orderTotalMinor);
        gc.setBalanceMinor(gc.getBalanceMinor() - covered);
        if (gc.getBalanceMinor() == 0) gc.setStatus("used_up");
        giftCards.save(gc);
        return covered;
    }

    @Transactional
    public LoyaltyAccount earnPoints(Long customerId, int points) {
        Long storeId = TenantContext.requireStoreId();
        LoyaltyAccount acct = loyalty.findByStoreIdAndCustomerId(storeId, customerId)
                .orElseGet(() -> {
                    Customer c = customers.findByIdAndStoreIdAndDeletedAtIsNull(customerId, storeId)
                            .orElseThrow(() -> ApiException.notFound("Customer"));
                    Store store = stores.findById(storeId).orElseThrow(() -> ApiException.notFound("Store"));
                    LoyaltyAccount a = new LoyaltyAccount();
                    a.setStore(store);
                    a.setCustomer(c);
                    return a;
                });
        acct.setPoints(acct.getPoints() + points);
        acct.setLifetimePoints(acct.getLifetimePoints() + points);
        acct.setTier(tierFor(acct.getLifetimePoints()));
        return loyalty.save(acct);
    }

    @Transactional
    public int redeemPoints(Long customerId, int points) {
        LoyaltyAccount acct = loyalty.findByStoreIdAndCustomerId(TenantContext.requireStoreId(), customerId)
                .orElseThrow(() -> ApiException.badRequest("NO_POINTS", "No loyalty account"));
        if (acct.getPoints() < points) {
            throw ApiException.badRequest("INSUFFICIENT_POINTS", "Not enough points");
        }
        acct.setPoints(acct.getPoints() - points);
        loyalty.save(acct);
        return points;
    }

    private static String tierFor(int lifetime) {
        if (lifetime >= 10000) return "platinum";
        if (lifetime >= 5000) return "gold";
        if (lifetime >= 1000) return "silver";
        return "bronze";
    }

    private static String generateCode() {
        byte[] b = new byte[9];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b).toUpperCase();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
