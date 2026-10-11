package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.catalog.repository.ProductVariantRepository;
import com.becho.saas.modules.inventory.entity.InventoryLevel;
import com.becho.saas.modules.inventory.repository.InventoryLevelRepository;
import com.becho.saas.modules.inventory.repository.LocationRepository;
import com.becho.saas.modules.inventory.service.InventoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Frontend-compatible inventory endpoints.
 */
@RestController
@RequestMapping("/stores/{storeId}/inventory")
public class StoreInventoryController {

    private final InventoryService inventoryService;
    private final InventoryLevelRepository levels;
    private final LocationRepository locations;
    private final ProductVariantRepository variants;
    private final StoreScope scope;
    private final JdbcTemplate jdbc;

    public StoreInventoryController(InventoryService inventoryService,
                                    InventoryLevelRepository levels,
                                    LocationRepository locations,
                                    ProductVariantRepository variants,
                                    StoreScope scope,
                                    JdbcTemplate jdbc) {
        this.inventoryService = inventoryService;
        this.levels = levels;
        this.locations = locations;
        this.variants = variants;
        this.scope = scope;
        this.jdbc = jdbc;
    }

    /** Resolve a location id only if it belongs to this store (IDOR guard). */
    private com.becho.saas.modules.inventory.entity.Location requireLocation(Long sid, Long locationId) {
        return locations.findByIdAndStoreIdAndDeletedAtIsNull(locationId, sid)
                .orElseThrow(() -> ApiException.notFound("Location"));
    }

    /** Resolve a variant id only if it belongs to this store (IDOR guard). */
    private com.becho.saas.modules.catalog.entity.ProductVariant requireVariant(Long sid, Long variantId) {
        return variants.findByIdAndStoreIdAndDeletedAtIsNull(variantId, sid)
                .orElseThrow(() -> ApiException.notFound("Variant"));
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> levels(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        return levels.findAll().stream()
                .filter(l -> l.getLocation() != null && l.getLocation().getStore() != null
                        && sid.equals(l.getLocation().getStore().getId()))
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("variantId", l.getVariant() != null ? l.getVariant().getId().toString() : "");
                    m.put("locationId", l.getLocation() != null ? l.getLocation().getId().toString() : "");
                    m.put("onHand", l.getOnHand());
                    m.put("qty", l.getOnHand()); // Frontend StockLevel contract
                    m.put("available", l.getAvailable());
                    return m;
                }).collect(Collectors.toList());
    }

    @PutMapping("/levels")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    @Transactional
    public Map<String, Object> set(@PathVariable String storeId,
                                   @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        Long variantId = requiredId(body.get("variantId"), "variantId");
        Long locationId = requiredId(body.get("locationId"), "locationId");
        requireVariant(sid, variantId);
        requireLocation(sid, locationId);
        int qty = requireWholeQuantity(body.get("qty"), true);
        var existing = levels.findForUpdate(locationId, variantId).orElse(null);
        if (existing != null && qty < existing.getReserved()) {
            throw ApiException.badRequest("QUANTITY_BELOW_RESERVED",
                    "On-hand quantity cannot be less than reserved stock");
        }
        inventoryService.adjust(locationId, variantId, qty, "manual adjustment via API");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("variantId", variantId.toString());
        m.put("locationId", locationId.toString());
        m.put("onHand", qty);
        return m;
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public List<Map<String, Object>> transfers(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, transfer_number, from_location_id, to_location_id, status, created_at " +
                        "FROM inventory_transfers WHERE store_id = ? ORDER BY created_at DESC, id DESC LIMIT 200", sid);
        for (Map<String, Object> row : rows) {
            Long transferId = ((Number) row.get("id")).longValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(transferId));
            item.put("transferNumber", String.valueOf(row.get("transfer_number")));
            item.put("fromId", String.valueOf(row.get("from_location_id")));
            item.put("toId", String.valueOf(row.get("to_location_id")));
            String dbStatus = String.valueOf(row.get("status"));
            item.put("status", "received".equalsIgnoreCase(dbStatus) ? "Completed" :
                    ("cancelled".equalsIgnoreCase(dbStatus) ? "Cancelled" : "Pending"));
            Object created = row.get("created_at");
            item.put("createdAt", created instanceof Timestamp ts ? ts.getTime() : System.currentTimeMillis());
            List<Map<String, Object>> items = jdbc.queryForList(
                    "SELECT variant_id, quantity FROM inventory_transfer_items WHERE transfer_id = ? ORDER BY id", transferId);
            item.put("items", items.stream().map(i -> {
                Map<String, Object> transferItem = new LinkedHashMap<>();
                transferItem.put("variantId", String.valueOf(i.get("variant_id")));
                transferItem.put("qty", ((Number) i.get("quantity")).intValue());
                return transferItem;
            }).toList());
            result.add(item);
        }
        return result;
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    @Transactional
    public Map<String, Object> transfer(@PathVariable String storeId,
                                        @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        Long fromId = requiredId(body.get("fromId"), "fromId");
        Long toId = requiredId(body.get("toId"), "toId");
        requireLocation(sid, fromId);
        requireLocation(sid, toId);
        if (fromId.equals(toId)) {
            throw ApiException.badRequest("INVALID_TRANSFER", "Source and destination must be different");
        }
        if (!(body.get("items") instanceof List<?> rawItems) || rawItems.isEmpty()) {
            throw ApiException.badRequest("INVALID_ITEMS", "At least one transfer item is required");
        }

        record TransferLine(Long variantId, int qty) {}
        List<TransferLine> items = new ArrayList<>();
        Set<Long> seenVariants = new HashSet<>();
        List<?> sortedRawItems = rawItems.stream().sorted((left, right) -> {
            String leftId = left instanceof Map<?, ?> m && m.get("variantId") instanceof String v ? v : "";
            String rightId = right instanceof Map<?, ?> m && m.get("variantId") instanceof String v ? v : "";
            return leftId.compareTo(rightId);
        }).toList();
        for (Object raw : sortedRawItems) {
            if (!(raw instanceof Map<?, ?> line)) {
                throw ApiException.badRequest("INVALID_ITEM", "Each item must be an object");
            }
            Long variantId = requiredId(line.get("variantId"), "variantId");
            int qty = requireWholeQuantity(line.get("qty"), false);
            requireVariant(sid, variantId);
            if (!seenVariants.add(variantId)) {
                throw ApiException.badRequest("DUPLICATE_VARIANT", "Each variant must appear only once");
            }
            // Lock both existing levels in a consistent location-id order to reduce deadlocks
            // when two transfers run in opposite directions at the same time.
            Long firstLocation = Math.min(fromId, toId);
            Long secondLocation = Math.max(fromId, toId);
            var firstLevel = levels.findForUpdate(firstLocation, variantId).orElse(null);
            var secondLevel = levels.findForUpdate(secondLocation, variantId).orElse(null);
            var src = fromId.equals(firstLocation) ? firstLevel : secondLevel;
            var dst = toId.equals(firstLocation) ? firstLevel : secondLevel;
            if (src == null || src.getAvailable() < qty) {
                throw ApiException.badRequest("INSUFFICIENT_STOCK", "Not enough available source stock");
            }
            int destinationOnHand = dst == null ? 0 : dst.getOnHand();
            try {
                Math.addExact(destinationOnHand, qty);
            } catch (ArithmeticException ex) {
                throw ApiException.badRequest("QUANTITY_OVERFLOW", "Destination stock quantity is too large");
            }
            items.add(new TransferLine(variantId, qty));
        }

        // Serialize transfer-number allocation for a given store.
        jdbc.queryForObject("SELECT id FROM stores WHERE id = ? FOR UPDATE", Long.class, sid);
        Long transferNumber = jdbc.queryForObject(
                "SELECT COALESCE(MAX(transfer_number), 0) + 1 FROM inventory_transfers WHERE store_id = ?", Long.class, sid);
        jdbc.update("INSERT INTO inventory_transfers " +
                        "(store_id, transfer_number, from_location_id, to_location_id, status) VALUES (?, ?, ?, ?, 'received')",
                sid, transferNumber, fromId, toId);
        Long transferId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (TransferLine line : items) {
            jdbc.update("INSERT INTO inventory_transfer_items (transfer_id, variant_id, quantity) VALUES (?, ?, ?)",
                    transferId, line.variantId(), line.qty());
            Long firstLocation = Math.min(fromId, toId);
            Long secondLocation = Math.max(fromId, toId);
            var firstLevel = levels.findForUpdate(firstLocation, line.variantId()).orElse(null);
            var secondLevel = levels.findForUpdate(secondLocation, line.variantId()).orElse(null);
            var src = fromId.equals(firstLocation) ? firstLevel : secondLevel;
            var dst = toId.equals(firstLocation) ? firstLevel : secondLevel;
            if (src == null) {
                throw ApiException.badRequest("INSUFFICIENT_STOCK", "Source stock is unavailable");
            }
            int destinationOnHand = dst == null ? 0 : dst.getOnHand();
            inventoryService.adjust(fromId, line.variantId(), src.getOnHand() - line.qty(), "transfer out");
            inventoryService.adjust(toId, line.variantId(), Math.addExact(destinationOnHand, line.qty()), "transfer in");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(transferId));
        result.put("fromId", fromId.toString());
        result.put("toId", toId.toString());
        result.put("items", items.stream().map(line -> {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("variantId", line.variantId().toString());
            i.put("qty", line.qty());
            return i;
        }).toList());
        result.put("status", "Completed");
        result.put("createdAt", System.currentTimeMillis());
        return result;
    }

    private Long requiredId(Object raw, String field) {
        if (!(raw instanceof String value) || value.isBlank()) {
            throw ApiException.badRequest("INVALID_ID", field + " is required");
        }
        return StoreScope.lid(value);
    }

    /** Validate JSON quantities without silently truncating decimals or defaulting missing values to zero. */
    private int requireWholeQuantity(Object raw, boolean allowZero) {
        if (!(raw instanceof Number number)) {
            throw ApiException.badRequest("INVALID_QUANTITY", "Quantity is required");
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.floor(value) || value > Integer.MAX_VALUE
                || (allowZero ? value < 0 : value < 1)) {
            throw ApiException.badRequest("INVALID_QUANTITY",
                    allowZero ? "Quantity must be a non-negative whole number" : "Quantity must be a positive whole number");
        }
        return (int) value;
    }

}
