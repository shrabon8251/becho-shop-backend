package com.becho.saas.modules.orders.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.catalog.entity.ProductVariant;
import com.becho.saas.modules.catalog.repository.ProductVariantRepository;
import com.becho.saas.modules.customers.repository.CustomerRepository;
import com.becho.saas.modules.discounts.service.DiscountService;
import com.becho.saas.modules.wallet.service.GiftCardService;
import com.becho.saas.modules.inventory.entity.Location;
import com.becho.saas.modules.inventory.repository.LocationRepository;
import com.becho.saas.modules.inventory.service.InventoryService;
import com.becho.saas.modules.orders.entity.*;
import com.becho.saas.modules.orders.repository.*;
import com.becho.saas.modules.orders.web.OrderDtos.*;
import com.becho.saas.modules.shipping.repository.ShippingRateRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.modules.notifications.NotificationService;
import com.becho.saas.modules.wallet.service.WalletService;
import com.becho.saas.modules.settings.StoreSettingsCache;
import com.becho.saas.tenancy.TenantContext;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;

/**
 * Single Responsibility: the order aggregate lifecycle.
 * Money recomputed server-side — client totals are never trusted.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderTransactionRepository transactions;
    private final StoreSequenceRepository sequences;
    private final ProductVariantRepository variants;
    private final CustomerRepository customers;
    private final StoreRepository stores;
    private final LocationRepository locations;
    private final ShippingRateRepository shippingRates;
    private final InventoryService inventory;
    private final NotificationService notifications;
    private final StoreSettingsCache settingsCache;
    private final DiscountService discounts;
    private final GiftCardService giftCards;
    private final WalletService wallet;

    public OrderService(OrderRepository orders, OrderTransactionRepository transactions,
                        StoreSequenceRepository sequences, ProductVariantRepository variants,
                        CustomerRepository customers, StoreRepository stores,
                        LocationRepository locations, ShippingRateRepository shippingRates,
                        InventoryService inventory, NotificationService notifications,
                        StoreSettingsCache settingsCache, DiscountService discounts,
                        GiftCardService giftCards, WalletService wallet) {
        this.orders = orders;
        this.transactions = transactions;
        this.sequences = sequences;
        this.variants = variants;
        this.customers = customers;
        this.stores = stores;
        this.locations = locations;
        this.shippingRates = shippingRates;
        this.inventory = inventory;
        this.notifications = notifications;
        this.settingsCache = settingsCache;
        this.discounts = discounts;
        this.giftCards = giftCards;
        this.wallet = wallet;
    }

    /** Creates an order + reserves stock in ONE transaction (ACID). */
    @Transactional
    public OrderDto create(CreateOrderRequest req) {
        Long storeId = TenantContext.requireStoreId();
        if (req.lines() == null || req.lines().isEmpty()) {
            throw ApiException.badRequest("NO_LINES", "Order needs at least one line item");
        }
        Store store = stores.findById(storeId).orElseThrow(() -> ApiException.notFound("Store"));

        Order order = new Order();
        order.setStore(store);
        order.setOrderNumber(nextOrderNumber(storeId));
        order.setEmail(req.email());
        order.setPhone(req.phone());
        order.setPaymentMethod(req.paymentMethod());
        order.setSource(req.source() == null ? "web" : req.source());
        order.setNotes(req.notes());
        order.setShippingAddress(req.shippingAddress());
        order.setBillingAddress(req.billingAddress());
        if (req.customerId() != null) {
            customers.findByIdAndStoreIdAndDeletedAtIsNull(req.customerId(), storeId)
                    .ifPresent(order::setCustomer);
        }

        long subtotal = 0;
        for (var lineReq : req.lines()) {
            ProductVariant v = variants.findByIdAndStoreIdAndDeletedAtIsNull(lineReq.variantId(), storeId)
                    .orElseThrow(() -> ApiException.notFound("Variant " + lineReq.variantId()));
            if (lineReq.quantity() <= 0) {
                throw ApiException.badRequest("BAD_QTY", "Quantity must be positive");
            }
            OrderLine line = new OrderLine();
            line.setOrder(order);
            line.setStore(store);
            line.setProduct(v.getProduct());
            line.setVariant(v);
            line.setTitle(v.getProduct().getTitle());
            line.setSku(v.getSku());
            line.setQuantity(lineReq.quantity());
            line.setPriceMinor(v.getPriceMinor()); // server-side price, never client-sent
            line.setDiscountMinor(0);
            line.setTaxMinor(0);
            line.setTotalMinor(v.getPriceMinor() * lineReq.quantity());
            subtotal += line.getTotalMinor();
            order.getLines().add(line);
        }

        order.setSubtotalMinor(subtotal);
        // Shipping price comes from the store's own rate table — the client only picks a rate id.
        long shippingMinor = 0;
        if (req.shippingRateId() != null) {
            shippingMinor = shippingRates.findByIdAndZoneStoreId(req.shippingRateId(), storeId)
                    .orElseThrow(() -> ApiException.badRequest("BAD_SHIPPING_RATE", "Unknown shipping rate"))
                    .getPriceMinor();
        }
        // Apply discount code if provided (validates + consumes one usage atomically).
        long discountMinor = 0;
        if (req.discountCode() != null && !req.discountCode().isBlank()) {
            var applied = discounts.consume(req.discountCode(), subtotal, req.customerId());
            discountMinor = applied.discountMinor();
            if (applied.freeShipping()) {
                shippingMinor = 0;
            }
        }
        order.setDiscountMinor(discountMinor);
        order.setShippingMinor(shippingMinor);
        order.setTaxMinor(0);
        long totalBeforeGift = subtotal - discountMinor + order.getShippingMinor();
        // Apply gift card if provided (pessimistic lock prevents double-spend).
        long giftApplied = 0;
        if (req.giftCode() != null && !req.giftCode().isBlank()) {
            var redemption = giftCards.redeem(req.giftCode(), totalBeforeGift);
            giftApplied = redemption.amountMinor();
            order.setGiftCodeHash(com.becho.saas.common.Hashing.sha256Hex(req.giftCode().trim().toUpperCase()));
            order.setGiftAppliedMinor(giftApplied);
        }
        if (req.discountCode() != null && !req.discountCode().isBlank()) {
            order.setDiscountCode(req.discountCode().trim().toUpperCase());
        }
        // Deduct loyalty points atomically (Bug #11: was frontend-only, race-prone).
        int pointsUsed = 0;
        if (req.loyaltyPoints() != null && req.loyaltyPoints() > 0 && req.customerId() != null) {
            pointsUsed = wallet.redeemPoints(req.customerId(), req.loyaltyPoints());
            order.setLoyaltyPointsUsed(pointsUsed);
        }
        // Calculate earned points from loyalty settings.
        int pointsEarned = 0;
        if (req.customerId() != null) {
            pointsEarned = calculateEarnedPoints(store.getSettings(), order.getTotalMinor());
            if (pointsEarned > 0) {
                wallet.earnPoints(req.customerId(), pointsEarned);
                order.setLoyaltyPointsEarned(pointsEarned);
            }
        }
        order.setTotalMinor(totalBeforeGift - giftApplied);
        order.setProcessedAt(LocalDateTime.now());

        // Resolve the fulfilling location (explicit or store default) and remember it
        // on the order so cancellation can release the exact reservations.
        Location location = resolveLocation(storeId, req.locationId());
        order.setLocation(location);
        orders.save(order);

        // Reserve stock for every line — same transaction, so failures roll back.
        for (OrderLine line : order.getLines()) {
            inventory.reserve(location.getId(), line.getVariant(), line.getQuantity(), order.getId());
        }
        OrderDto dto = toDto(order);
        // Notify per merchant settings (best-effort, never fails the order).
        try {
            String settings = store.getSettings();
            Map<String, String> vars = Map.of(
                    "name", order.getEmail() == null ? "" : order.getEmail(),
                    "order_id", "#" + order.getOrderNumber(),
                    "amount", formatAmount(order.getTotalMinor(), order.getCurrency()),
                    "store", store.getName(),
                    "tracking_link", "");
            notifications.dispatch(settings, "order_placed", vars,
                    order.getEmail(), order.getPhone());
        } catch (Exception e) {
            // Notifications are best-effort.
        }
        return dto;
    }

    private String formatAmount(long minor, String currency) {
        return String.format("%s %.2f", currency == null ? "BDT" : currency, minor / 100.0);
    }

    /**
     * Validates a refund: amount must be positive and must not exceed
     * (order total - already refunded).
     */
    private void validateRefund(Order order, long amountMinor) {
        if (amountMinor <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_AMOUNT",
                    "Refund amount must be positive");
        }
        long alreadyRefunded = transactions
                .findByOrderIdAndKindAndStatus(order.getId(), "refund", "success")
                .stream().mapToLong(OrderTransaction::getAmountMinor).sum();
        long refundable = order.getTotalMinor() - alreadyRefunded;
        if (amountMinor > refundable) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_EXCEEDS_TOTAL",
                    "Refund amount exceeds the refundable total");
        }
    }

    private Location resolveLocation(Long storeId, Long locationId) {
        if (locationId != null) {
            return locations.findByIdAndStoreIdAndDeletedAtIsNull(locationId, storeId)
                    .orElseThrow(() -> ApiException.badRequest("BAD_LOCATION", "Unknown location"));
        }
        return locations.findByStoreIdAndDeletedAtIsNull(storeId).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("NO_LOCATION", "Store has no location"));
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        Order o = orders.findWithLines(id, TenantContext.requireStoreId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        return toDto(o);
    }

    /** Record a payment attempt outcome. Idempotent on gateway_txn_id. */
    @Transactional
    public OrderDto recordTransaction(Long orderId, RecordTransactionRequest req) {
        Long storeId = TenantContext.requireStoreId();
        Order order = orders.findByIdAndStoreIdAndDeletedAtIsNull(orderId, storeId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (req.gatewayTxnId() != null
                && transactions.existsByStoreIdAndGatewayAndGatewayTxnId(storeId, req.gateway(), req.gatewayTxnId())) {
            return toDto(order); // already processed — IPN retries are safe
        }
        // Validate refund amounts: positive and not exceeding the refundable total.
        if ("refund".equals(req.kind())) {
            validateRefund(order, req.amountMinor());
        }
        OrderTransaction t = new OrderTransaction();
        t.setOrder(order);
        t.setStoreId(storeId);
        t.setKind(req.kind());
        t.setGateway(req.gateway());
        t.setAmountMinor(req.amountMinor());
        t.setCurrency(order.getCurrency());
        t.setStatus(req.status());
        t.setGatewayTxnId(req.gatewayTxnId());
        t.setRaw(req.raw());
        transactions.save(t);

        if ("sale".equals(req.kind()) && "success".equals(req.status())) {
            order.setFinancialStatus("paid");
        } else if ("refund".equals(req.kind()) && "success".equals(req.status())) {
            order.setFinancialStatus("refunded");
        }
        return toDto(orders.save(order));
    }

    @Transactional
    public OrderDto cancel(Long id) {
        Long storeId = TenantContext.requireStoreId();
        Order order = orders.findWithLines(id, storeId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (!"unfulfilled".equals(order.getFulfillmentStatus())) {
            throw ApiException.badRequest("ALREADY_FULFILLED", "Cannot cancel a fulfilled order");
        }
        if (order.getCancelledAt() != null) {
            return toDto(order); // idempotent
        }
        // Release the reservations made at creation, using the stored location.
        if (order.getLocation() != null) {
            Long locationId = order.getLocation().getId();
            for (OrderLine line : order.getLines()) {
                if (line.getVariant() != null) {
                    inventory.release(locationId, line.getVariant().getId(), line.getQuantity(), order.getId());
                }
            }
        }
        // Reverse discount usage so the code can be used again.
        if (order.getDiscountCode() != null && !order.getDiscountCode().isBlank()) {
            discounts.release(order.getDiscountCode());
        }
        // Refund gift card balance.
        if (order.getGiftCodeHash() != null && order.getGiftAppliedMinor() > 0) {
            giftCards.refund(order.getGiftCodeHash(), order.getGiftAppliedMinor());
        }
        // Refund loyalty points (Bug #11).
        if (order.getCustomer() != null) {
            Long customerId = order.getCustomer().getId();
            if (order.getLoyaltyPointsUsed() > 0) {
                wallet.earnPoints(customerId, order.getLoyaltyPointsUsed());
            }
            if (order.getLoyaltyPointsEarned() > 0) {
                try {
                    wallet.redeemPoints(customerId, order.getLoyaltyPointsEarned());
                } catch (Exception e) {
                    // If customer spent the earned points already, just skip reversal.
                }
            }
        }
        order.setCancelledAt(LocalDateTime.now());
        order.setFinancialStatus("voided");
        Order saved = orders.save(order);
        // Notify per merchant settings (best-effort).
        try {
            String settings = settingsCache.getSettings(storeId);
            final String fSettings = settings;
            final String fEmail = saved.getEmail();
            final String fPhone = saved.getPhone();
            final String fOrderNum = "#" + saved.getOrderNumber();
            final String fAmount = formatAmount(saved.getTotalMinor(), saved.getCurrency());
            final String fStoreName = stores.findById(storeId).map(s -> s.getName()).orElse("");
            notifyAfterCommit(() ->
                    notifications.dispatch(fSettings, "order_cancelled", Map.of(
                                    "name", fEmail == null ? "" : fEmail,
                                    "order_id", fOrderNum,
                                    "amount", fAmount,
                                    "store", fStoreName,
                                    "tracking_link", ""),
                            fEmail, fPhone));
        } catch (Exception e) {
            // Notifications are best-effort.
        }
        return toDto(saved);
    }

    /**
     * Marks an order fulfilled: converts reserved stock to sold (releases the
     * reservation AND decrements on-hand). Idempotent. Cancelled or already
     * fulfilled orders are rejected.
     */
    @Transactional
    public OrderDto fulfill(Long id) {
        Long storeId = TenantContext.requireStoreId();
        Order order = orders.findWithLines(id, storeId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getCancelledAt() != null) {
            throw ApiException.badRequest("ALREADY_CANCELLED", "Cannot fulfill a cancelled order");
        }
        if ("fulfilled".equals(order.getFulfillmentStatus())) {
            return toDto(order); // idempotent
        }
        if (order.getLocation() != null) {
            Long locationId = order.getLocation().getId();
            for (OrderLine line : order.getLines()) {
                if (line.getVariant() != null) {
                    inventory.fulfill(locationId, line.getVariant().getId(), line.getQuantity(), order.getId());
                }
            }
        }
        order.setFulfillmentStatus("fulfilled");
        return toDto(orders.save(order));
    }

    private long nextOrderNumber(Long storeId) {
        StoreSequence seq = sequences.findByStoreIdAndSeqName(storeId, "order").orElseGet(() -> {
            StoreSequence s = new StoreSequence();
            s.setStoreId(storeId);
            s.setSeqName("order");
            return s;
        });
        long n = seq.takeNext();
        sequences.save(seq);
        return n;
    }

    private OrderDto toDto(Order o) {
        var lines = o.getLines().stream()
                .map(l -> new LineDto(l.getId(), l.getVariant() == null ? null : l.getVariant().getId(),
                        l.getTitle(), l.getSku(), l.getQuantity(), l.getPriceMinor(), l.getTotalMinor()))
                .toList();
        return new OrderDto(o.getId(), o.getOrderNumber(), o.getFinancialStatus(),
                o.getFulfillmentStatus(), o.getSubtotalMinor(), o.getDiscountMinor(),
                o.getShippingMinor(), o.getTaxMinor(), o.getTotalMinor(), o.getCurrency(),
                o.getPaymentMethod(), o.getEmail(), o.getPhone(), o.getCreatedAt(), lines,
                o.getDiscountCode(), o.getGiftAppliedMinor(),
                o.getLoyaltyPointsUsed(), o.getLoyaltyPointsEarned());
    }

    /**
     * Runs the notification dispatch only after the current transaction
     * commits successfully. If no transaction is active (e.g. in tests),
     * runs immediately.
     */
    /**
     * Calculates loyalty points earned for an order based on store settings.
     * Matches frontend formula: floor(total / earnPer) * earnPoints.
     */
    private int calculateEarnedPoints(String settingsJson, long totalMinor) {
        try {
            if (settingsJson == null || settingsJson.isBlank()) return 0;
            var mapper = new ObjectMapper();
            var root = mapper.readTree(settingsJson);
            var loyalty = root.get("loyalty");
            if (loyalty == null || !loyalty.path("enabled").asBoolean(false)) return 0;
            long earnPer = loyalty.path("earnPer").asLong(0);
            int earnPoints = loyalty.path("earnPoints").asInt(0);
            if (earnPer <= 0 || earnPoints <= 0) return 0;
            // totalMinor is in minor units; earnPer is in major units (BDT).
            long totalMajor = totalMinor / 100;
            return (int) (totalMajor / earnPer * earnPoints);
        } catch (Exception e) {
            return 0;
        }
    }

    private void notifyAfterCommit(Runnable dispatch) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                dispatch.run();
                            } catch (Exception e) {
                                // Notifications are best-effort.
                            }
                        }
                    });
        } else {
            try {
                dispatch.run();
            } catch (Exception e) {
                // Notifications are best-effort.
            }
        }
    }
}
