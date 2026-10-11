package com.becho.saas.modules.discounts.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.discounts.entity.Discount;
import com.becho.saas.modules.discounts.repository.DiscountRepository;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * Validates + computes discount amounts server-side.
 * Pure calculation — no persistence side effects except usage counting.
 */
@Service
public class DiscountService {

    private final DiscountRepository discounts;

    public DiscountService(DiscountRepository discounts) {
        this.discounts = discounts;
    }

    public record AppliedDiscount(Long discountId, String code, long discountMinor, boolean freeShipping) {}

    /**
     * Pure preview: checks eligibility and computes the amount WITHOUT touching
     * usage counters. Safe to call from checkout previews.
     */
    @Transactional(readOnly = true)
    public AppliedDiscount validate(String code, long subtotalMinor, Long customerId) {
        Discount d = loadValid(code, subtotalMinor);
        long amount = amountFor(d, subtotalMinor);
        return new AppliedDiscount(d.getId(), d.getCode(), amount,
                "free_shipping".equals(d.getDiscountType()));
    }

    /**
     * Consumes one usage of the code. Call exactly once when the order is placed,
     * in the same transaction as order creation. The row is pessimistically
     * locked so concurrent checkouts cannot overrun the usage limit.
     */
    @Transactional
    public AppliedDiscount consume(String code, long subtotalMinor, Long customerId) {
        Discount d = loadValidForUpdate(code, subtotalMinor);
        d.setUsedCount(d.getUsedCount() + 1);
        discounts.save(d);
        long amount = amountFor(d, subtotalMinor);
        return new AppliedDiscount(d.getId(), d.getCode(), amount,
                "free_shipping".equals(d.getDiscountType()));
    }

    private Discount loadValidForUpdate(String code, long subtotalMinor) {
        Long storeId = TenantContext.requireStoreId();
        Discount d = discounts.findByStoreIdAndCodeForUpdate(storeId, code.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CODE_INVALID", "Discount code is invalid"));
        validateWindow(d, subtotalMinor);
        return d;
    }

    private Discount loadValid(String code, long subtotalMinor) {
        Long storeId = TenantContext.requireStoreId();
        Discount d = discounts.findByStoreIdAndCodeAndDeletedAtIsNull(storeId, code.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CODE_INVALID", "Discount code is invalid"));
        validateWindow(d, subtotalMinor);
        return d;
    }

    private void validateWindow(Discount d, long subtotalMinor) {
        LocalDateTime now = LocalDateTime.now();
        if (!"active".equals(d.getStatus())) throw invalid("CODE_INACTIVE", "Discount code is not active");
        if (d.getStartsAt() != null && now.isBefore(d.getStartsAt())) throw invalid("CODE_NOT_STARTED", "Discount not started yet");
        if (d.getEndsAt() != null && now.isAfter(d.getEndsAt())) throw invalid("CODE_EXPIRED", "Discount code expired");
        if (d.getUsageLimit() != null && d.getUsedCount() >= d.getUsageLimit())
            throw invalid("CODE_EXHAUSTED", "Discount usage limit reached");
        if (d.getMinimumSubtotalMinor() != null && subtotalMinor < d.getMinimumSubtotalMinor())
            throw invalid("MIN_SUBTOTAL", "Minimum order amount not met");
    }

    private long amountFor(Discount d, long subtotalMinor) {
        return switch (d.getDiscountType()) {
            case "percentage" -> d.getValuePercent() == null ? 0
                    : subtotalMinor * d.getValuePercent().longValue() / 100;
            case "fixed_amount" -> Math.min(d.getValueMinor(), subtotalMinor);
            case "free_shipping" -> 0;
            default -> throw invalid("UNSUPPORTED", "Unsupported discount type");
        };
    }

    @Transactional
    public Discount create(Discount d) {
        // BUG-34 fix: null-safe code — a missing code used to NPE here (500).
        // The storefront controller normally defaults it, but never trust the caller.
        String code = d.getCode() == null || d.getCode().isBlank()
                ? "AUTO-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : d.getCode().trim().toUpperCase();
        d.setCode(code);
        return discounts.save(d);
    }

    /**
     * Releases one usage of the code (e.g. when the order is cancelled).
     * Decrements usedCount so the code can be used again.
     */
    @Transactional
    public void release(String code) {
        Long storeId = TenantContext.requireStoreId();
        discounts.findByStoreIdAndCodeAndDeletedAtIsNull(storeId, code.trim().toUpperCase())
                .ifPresent(d -> {
                    d.setUsedCount(Math.max(0, d.getUsedCount() - 1));
                    discounts.save(d);
                });
    }

    private static ApiException invalid(String code, String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, msg);
    }
}
