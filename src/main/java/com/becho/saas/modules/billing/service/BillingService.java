package com.becho.saas.modules.billing.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.billing.entity.PromoCode;
import com.becho.saas.modules.billing.entity.PromoRedemption;
import com.becho.saas.modules.billing.repository.PromoCodeRepository;
import com.becho.saas.modules.billing.repository.PromoRedemptionRepository;
import com.becho.saas.modules.payments.providers.SslCommerzProvider;
import com.becho.saas.modules.payments.PaymentProvider;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform billing: plan prices, billing cycles, and promo codes are all
 * validated and priced SERVER-SIDE. The frontend shows prices, but the
 * backend recomputes everything — a client can never get a discount the
 * server didn't approve.
 *
 * <p>Plans (BDT/month): Starter 399, Growth 999, Pro 1999.
 * BUG-41 fix: these must match the frontend's PLANS (src/lib/plans.ts) —
 * the heading showed 999 while the quote button showed 799.
 * Cycles: monthly ×1 (no off), quarterly ×3 (−10%), half-yearly ×6 (−20%),
 * yearly ×12 (−30%). Promo (e.g. BECHO10) takes percent_off off the
 * cycle-discounted subtotal.
 *
 * <p>Promo redemption is atomic: the promo row is locked (SELECT ... FOR
 * UPDATE), usage is validated and incremented, and the UNIQUE(code, store_id)
 * on promo_redemptions rejects any double redemption — including races.
 */
@Service
public class BillingService {

    /** Monthly base prices in BDT (whole taka). */
    public static final Map<String, Integer> PLAN_PRICES = Map.of(
            "Starter", 399,
            "Growth", 999,
            "Pro", 1999);

    public record Cycle(String id, int months, BigDecimal off) {}

    public static final List<Cycle> CYCLES = List.of(
            new Cycle("monthly", 1, BigDecimal.ZERO),
            new Cycle("quarterly", 3, new BigDecimal("0.10")),
            new Cycle("halfyearly", 6, new BigDecimal("0.20")),
            new Cycle("yearly", 12, new BigDecimal("0.30")));

    private final PromoCodeRepository promoCodes;
    private final PromoRedemptionRepository redemptions;
    private final StoreRepository stores;
    private final SslCommerzProvider sslCommerz;

    public BillingService(PromoCodeRepository promoCodes,
                          PromoRedemptionRepository redemptions,
                          StoreRepository stores,
                          SslCommerzProvider sslCommerz) {
        this.promoCodes = promoCodes;
        this.redemptions = redemptions;
        this.stores = stores;
        this.sslCommerz = sslCommerz;
    }

    /**
     * Initiate SSLCommerz payment for plan subscription.
     * Returns GatewayPageURL for redirect.
     */
    public Map<String, Object> initiateSslCommerz(Long storeId, Map<String, Object> body) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Store"));
        String plan = (String) body.getOrDefault("plan", store.getPlanName());
        String cycle = (String) body.getOrDefault("cycle", "monthly");

        Map<String, Object> quote = quote(plan, cycle, (String) body.get("promoCode"));
        long totalMinor = ((Number) quote.get("total")).longValue() * 100;

        String backendUrl = "https://becho-shop-backend.onrender.com";
        var req = new PaymentProvider.InitRequest(
                storeId,
                System.currentTimeMillis(),
                totalMinor,
                "BDT",
                "billing@becho.shop",
                "",
                backendUrl + "/stores/" + storeId + "/billing/sslcommerz/success",
                backendUrl + "/stores/" + storeId + "/billing/sslcommerz/failed",
                backendUrl + "/stores/" + storeId + "/billing/sslcommerz/cancelled"
        );
        var result = sslCommerz.initPayment(req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gatewayUrl", result.checkoutUrl());
        m.put("sessionKey", result.gatewaySessionId());
        return m;
    }

    /** Server-side price breakdown. Never trust client-computed totals. */
    public Map<String, Object> quote(String plan, String cycleId, String promoCode) {
        int monthly = planPrice(plan);
        Cycle cycle = cycleOf(cycleId);

        BigDecimal full = BigDecimal.valueOf((long) monthly * cycle.months());
        BigDecimal cycleDiscount = full.multiply(cycle.off()).setScale(0, RoundingMode.HALF_UP);
        BigDecimal subtotal = full.subtract(cycleDiscount);

        BigDecimal promoOff = BigDecimal.ZERO;
        String appliedPromo = null;
        if (promoCode != null && !promoCode.isBlank()) {
            PromoCode promo = lookupPromo(promoCode); // validates active/uses/expiry (no lock needed for quote)
            promoOff = subtotal.multiply(promo.getPercentOff())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            appliedPromo = promo.getCode();
        }
        BigDecimal total = subtotal.subtract(promoOff);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", plan);
        m.put("cycle", cycle.id());
        m.put("months", cycle.months());
        m.put("currency", "BDT");
        m.put("monthlyPrice", monthly);
        m.put("fullPrice", full.longValue());
        m.put("cycleDiscount", cycleDiscount.longValue());
        m.put("subtotal", subtotal.longValue());
        m.put("promoCode", appliedPromo);
        m.put("promoDiscount", promoOff.longValue());
        m.put("total", total.longValue());
        return m;
    }

    /**
     * Subscribe a store to a plan. Validates the promo atomically and records
     * the redemption; then stores the chosen plan on the store.
     */
    @Transactional
    public Map<String, Object> subscribe(Long storeId, String plan, String cycleId, String promoCode) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Store"));

        BigDecimal promoOff = BigDecimal.ZERO;
        String appliedPromo = null;
        if (promoCode != null && !promoCode.isBlank()) {
            String code = promoCode.trim().toUpperCase();
            // Atomic: lock row -> validate -> increment -> record redemption.
            PromoCode promo = promoCodes.findByCodeForUpdate(code)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PROMO_INVALID",
                            "Promo code is invalid"));
            if (!promo.isUsable()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROMO_INVALID",
                        "Promo code is expired, inactive, or fully used");
            }
            if (redemptions.existsByPromoCodeCodeAndStoreId(code, storeId)) {
                throw new ApiException(HttpStatus.CONFLICT, "PROMO_ALREADY_USED",
                        "This promo code was already used by this store");
            }
            promo.setUsedCount(promo.getUsedCount() + 1);
            promoCodes.save(promo);
            PromoRedemption r = new PromoRedemption();
            r.setPromoCode(promo);
            r.setStore(store);
            try {
                redemptions.save(r);
            } catch (DataIntegrityViolationException e) {
                // Lost a race with another request: the UNIQUE constraint won.
                throw new ApiException(HttpStatus.CONFLICT, "PROMO_ALREADY_USED",
                        "This promo code was already used by this store");
            }
            appliedPromo = code;
            // Recompute the discount from the locked promo (percent can't change mid-txn).
            Map<String, Object> q = quote(plan, cycleId, null);
            BigDecimal subtotal = BigDecimal.valueOf((Long) q.get("subtotal"));
            promoOff = subtotal.multiply(promo.getPercentOff())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        }

        // DO NOT activate the plan here — payment must be confirmed first.
        // The plan goes live only after a successful payment callback.
        store.setPlanName(plan);
        store.setBillingCycle(cycleId);
        store.setBillingStatus("pending_payment");
        stores.save(store);

        Map<String, Object> q = quote(plan, cycleId, null);
        Map<String, Object> m = new LinkedHashMap<>(q);
        m.put("promoCode", appliedPromo);
        m.put("promoDiscount", promoOff.longValue());
        m.put("total", ((Long) q.get("subtotal")) - promoOff.longValue());
        m.put("subscribed", false);
        m.put("paymentRequired", true);
        m.put("billingStatus", "pending_payment");
        return m;
    }

    /**
     * Activates a pending subscription after payment confirmation.
     * Called by the payment callback — never directly by the client.
     */
    @Transactional
    public void activateSubscription(Long storeId) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Store"));
        if (!"pending_payment".equals(store.getBillingStatus())) {
            return; // idempotent
        }
        store.setBillingStatus("active");
        stores.save(store);
    }

    private static final java.util.Set<String> BILLING_METHOD_KINDS =
            java.util.Set.of("bkash", "nagad", "rocket", "sslcommerz");

    /** Save the merchant's subscription payment method (fixes 404 on PUT /billing/method). */
    @Transactional
    public Map<String, Object> saveBillingMethod(Long storeId, String kind, String label, String trxId) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Store"));
        String k = kind == null ? "" : kind.trim().toLowerCase();
        if (!BILLING_METHOD_KINDS.contains(k)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_METHOD", "Unknown payment method");
        }
        if (!"sslcommerz".equals(k) && (trxId == null || trxId.trim().length() < 4)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRXID_REQUIRED",
                    "Enter the Transaction ID (TrxID) for manual payment methods");
        }
        store.setBillingMethodKind(k);
        store.setBillingMethodLabel(label == null || label.isBlank() ? k : label.trim());
        store.setBillingMethodTrxid(trxId == null ? null : trxId.trim().toUpperCase());
        stores.save(store);
        return billingMethodOf(store);
    }

    /** Return the saved subscription payment method (null when none saved). */
    @Transactional(readOnly = true)
    public Map<String, Object> getBillingMethod(Long storeId) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Store"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("method", store.getBillingMethodKind() == null ? null : billingMethodOf(store));
        return m;
    }

    private Map<String, Object> billingMethodOf(Store store) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", store.getBillingMethodKind());
        m.put("label", store.getBillingMethodLabel());
        m.put("trxId", store.getBillingMethodTrxid());
        return m;
    }

    /**
     * Confirm a pending subscription payment. For manual methods (bKash/Nagad/
     * Rocket) the merchant's TrxID is required; SSLCommerz subscriptions are
     * confirmed via the gateway callback. Flips billing_status to active.
     */
    @Transactional
    public Map<String, Object> confirmSubscriptionPayment(Long storeId, String trxId) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Store"));
        if (!"pending_payment".equals(store.getBillingStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_PENDING",
                    "No pending subscription payment to confirm");
        }
        String kind = store.getBillingMethodKind();
        if ("sslcommerz".equals(kind)) {
            // SSLCommerz payments are verified via gateway callback/IPN only.
            // The merchant cannot self-confirm — that would grant paid plans for free.
            throw new ApiException(HttpStatus.BAD_REQUEST, "GATEWAY_VERIFY_REQUIRED",
                    "SSLCommerz payments are confirmed automatically after gateway verification. Please complete the payment.");
        }
        if (trxId == null || trxId.trim().length() < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRXID_REQUIRED",
                    "Enter the Transaction ID (TrxID) from your payment");
        }
        store.setBillingTrxId(trxId.trim().toUpperCase());
        store.setBillingStatus("active");
        stores.save(store);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("billingStatus", "active");
        m.put("plan", store.getPlanName());
        m.put("cycle", store.getBillingCycle());
        return m;
    }

    /** Validate a promo without redeeming (used by quote). */
    private PromoCode lookupPromo(String rawCode) {
        String code = rawCode.trim().toUpperCase();
        PromoCode promo = promoCodes.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PROMO_INVALID",
                        "Promo code is invalid"));
        if (!promo.isUsable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROMO_INVALID",
                    "Promo code is expired, inactive, or fully used");
        }
        return promo;
    }

    private int planPrice(String plan) {
        Integer p = PLAN_PRICES.get(plan);
        if (p == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_INVALID",
                    "Unknown plan: " + plan + ". Valid: " + PLAN_PRICES.keySet());
        }
        return p;
    }

    private Cycle cycleOf(String cycleId) {
        return CYCLES.stream()
                .filter(c -> c.id().equals(cycleId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CYCLE_INVALID",
                        "Unknown billing cycle: " + cycleId));
    }
}
