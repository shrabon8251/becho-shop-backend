package com.becho.saas.modules.inventory.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.catalog.entity.ProductVariant;
import com.becho.saas.modules.catalog.repository.ProductVariantRepository;
import com.becho.saas.modules.inventory.entity.*;
import com.becho.saas.modules.inventory.repository.*;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single Responsibility: stock quantities only.
 * All mutations are atomic; every change writes a stock_movements audit row.
 */
@Service
public class InventoryService {

    private final InventoryLevelRepository levels;
    private final LocationRepository locations;
    private final StockMovementRepository movements;
    private final ProductVariantRepository variants;

    public InventoryService(InventoryLevelRepository levels, LocationRepository locations,
                            StockMovementRepository movements, ProductVariantRepository variants) {
        this.levels = levels;
        this.locations = locations;
        this.movements = movements;
        this.variants = variants;
    }

    /** Reserve stock for an order. Throws if insufficient (unless policy=continue). */
    @Transactional
    public void reserve(Long locationId, ProductVariant variant, int qty, Long orderId) {
        InventoryLevel level = lockLevel(locationId, variant.getId());
        if (level.getAvailable() < qty && !"continue".equals(variant.getInventoryPolicy())) {
            throw ApiException.badRequest("INSUFFICIENT_STOCK",
                    "Only " + level.getAvailable() + " available for SKU " + variant.getSku());
        }
        level.setReserved(level.getReserved() + qty);
        levels.save(level);
        // reservation doesn't change on_hand -> delta 0, but audit the commitment
        audit(variant.getId(), locationId, 0, "order_reserve", "order", orderId);
    }

    @Transactional
    public void release(Long locationId, Long variantId, int qty, Long orderId) {
        InventoryLevel level = lockLevel(locationId, variantId);
        level.setReserved(Math.max(0, level.getReserved() - qty));
        levels.save(level);
        audit(variantId, locationId, 0, "order_release", "order", orderId);
    }

    /** Convert reserved -> sold on fulfillment. */
    @Transactional
    public void fulfill(Long locationId, Long variantId, int qty, Long fulfillmentId) {
        InventoryLevel level = lockLevel(locationId, variantId);
        if (qty <= 0 || qty > level.getReserved()) {
            throw ApiException.badRequest("INVALID_FULFILL_QTY",
                    "Fulfill quantity must be within reserved stock");
        }
        level.setReserved(level.getReserved() - qty);
        level.setOnHand(level.getOnHand() - qty);
        levels.save(level);
        audit(variantId, locationId, -qty, "fulfill", "fulfillment", fulfillmentId);
    }

    // BUG-29 fix: NO @Transactional here — runs in the caller's transaction.
    // REQUIRES_NEW broke it (inner tx couldn't see the uncommitted variant).
    // Caller (CatalogService.adjustStock) wraps in try-catch so a failure
    // never poisons the product-create transaction.
    public void adjust(Long locationId, Long variantId, int newOnHand, String reason) {
        InventoryLevel level = lockLevel(locationId, variantId);
        int delta = newOnHand - level.getOnHand();
        level.setOnHand(newOnHand);
        levels.save(level);
        audit(variantId, locationId, delta, "adjustment:" + reason, null, null);
    }

    private InventoryLevel lockLevel(Long locationId, Long variantId) {
        return levels.findForUpdate(locationId, variantId).orElseGet(() -> {
            // Auto-create the level row on first use (e.g. new variant, manual adjustment).
            InventoryLevel level = new InventoryLevel();
            level.setLocation(locations.findById(locationId)
                    .orElseThrow(() -> ApiException.notFound("Location")));
            level.setVariant(variants.findById(variantId)
                    .orElseThrow(() -> ApiException.notFound("Variant")));
            level.setOnHand(0);
            level.setReserved(0);
            return levels.save(level);
        });
    }

    private void audit(Long variantId, Long locationId, int delta, String reason,
                       String refType, Long refId) {
        StockMovement m = new StockMovement();
        m.setStoreId(TenantContext.requireStoreId());
        m.setVariantId(variantId);
        m.setLocationId(locationId);
        m.setDelta(delta);
        m.setReason(reason);
        m.setRefType(refType);
        m.setRefId(refId);
        movements.save(m);
    }
}
