package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.analytics.entity.StoreVisit;
import com.becho.saas.modules.analytics.repository.StoreVisitRepository;
import com.becho.saas.modules.catalog.service.CatalogService;
import com.becho.saas.modules.platform.entity.Theme;
import com.becho.saas.modules.platform.repository.ThemeRepository;
import com.becho.saas.modules.settings.StoreSettingsCache;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public storefront read endpoints for anonymous shoppers.
 * Base path: /api/storefront/stores/{storeId}/...
 *
 * <p>No JWT required (permitAll in SecurityConfig). Rate-limited.
 * All queries are store-scoped via the URL storeId. Responses use
 * public DTOs — no cost, supplier, credential, or staff-only fields.
 */
@RestController
@RequestMapping("/api/storefront/stores/{storeId}")
public class PublicStorefrontController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CatalogService catalogService;
    private final StoreSettingsCache settingsCache;
    private final StoreRepository stores;
    private final ThemeRepository themes;
    private final com.becho.saas.modules.discounts.repository.DiscountRepository discounts;
    private final com.becho.saas.modules.platform.repository.StoreAppsConfigRepository appsConfigs;
    private final com.becho.saas.modules.catalog.repository.CollectionRepository collections;
    private final com.becho.saas.modules.catalog.repository.BundleRepository bundles;
    private final com.becho.saas.modules.platform.repository.PageRepository pages;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final StoreVisitRepository visits;

    public PublicStorefrontController(
            CatalogService catalogService,
            StoreSettingsCache settingsCache,
            StoreRepository stores,
            ThemeRepository themes,
            com.becho.saas.modules.discounts.repository.DiscountRepository discounts,
            com.becho.saas.modules.platform.repository.StoreAppsConfigRepository appsConfigs,
            com.becho.saas.modules.catalog.repository.CollectionRepository collections,
            com.becho.saas.modules.catalog.repository.BundleRepository bundles,
            com.becho.saas.modules.platform.repository.PageRepository pages,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            StoreVisitRepository visits) {
        this.catalogService = catalogService;
        this.settingsCache = settingsCache;
        this.stores = stores;
        this.themes = themes;
        this.discounts = discounts;
        this.appsConfigs = appsConfigs;
        this.collections = collections;
        this.bundles = bundles;
        this.pages = pages;
        this.jdbc = jdbc;
        this.visits = visits;
    }

    /** Parses and validates the storeId from the URL (no JWT needed). */
    private Long sid(String storeId) {
        try {
            Long id = Long.valueOf(storeId);
            if (!stores.existsById(id)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "Store not found");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_ID", "Invalid store id: " + storeId);
        }
    }

    // ---------------------------------------------------------------- store info

    /** Public store card for the storefront header: name + uploaded logo.
     *  The logo lives on the Store (logo_url, Batch A) — this is the single
     *  source of truth, so the header and the admin sidebar can never drift. */
    @GetMapping
    public Map<String, Object> store(@PathVariable String storeId) {
        Long id = sid(storeId);
        Store s = stores.findById(id).orElseThrow(() -> ApiException.notFound("Store"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("name", s.getName());
        m.put("subdomain", s.getSubdomain());
        m.put("logoUrl", s.getLogoUrl());
        m.put("currency", s.getCurrency() != null ? s.getCurrency() : "BDT");
        return m;
    }

    // ---------------------------------------------------------------- visits (beacon)

    /**
     * Visitor beacon: POST /api/storefront/stores/{storeId}/visits.
     * Records one row per (store, day, visitor) for the dashboard Conversion
     * Rate card. The visitor is a SHA-256 hash of (IP + User-Agent + day) —
     * the raw IP / User-Agent is never stored. Always 200: a duplicate visit
     * or a storage hiccup must never break the shopper's page. Rate-limited
     * with the other public storefront reads (120/min per IP).
     */
    @PostMapping("/visits")
    public Map<String, Object> visit(@PathVariable String storeId, HttpServletRequest req) {
        Long id = sid(storeId);
        LocalDate today = LocalDate.now();
        String hash = visitorHash(clientIp(req), req.getHeader("User-Agent"), today);
        boolean recorded = false;
        try {
            if (!visits.existsByStoreIdAndVisitedDateAndVisitorHash(id, today, hash)) {
                Store store = stores.getReferenceById(id);
                StoreVisit v = new StoreVisit();
                v.setStore(store);
                v.setVisitedDate(today);
                v.setVisitorHash(hash);
                visits.save(v);
                recorded = true;
            }
        } catch (Exception e) {
            // Best-effort tracking: never fail the request (e.g. a race on
            // the unique constraint just means the visit was already counted).
            recorded = false;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recorded", recorded);
        return m;
    }

    private static String visitorHash(String ip, String ua, LocalDate day) {
        try {
            String raw = (ip == null ? "" : ip) + "|" + (ua == null ? "" : ua) + "|" + day;
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "unavailable";
        }
    }

    /** First X-Forwarded-For entry when behind a proxy, else the remote address. */
    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    // ---------------------------------------------------------------- products

    @GetMapping("/products")
    public List<Map<String, Object>> products(@PathVariable String storeId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer size) {
        Long id = sid(storeId);
        TenantContext.set(id, null);
        try {
            var page = catalogService.listProducts("active", cursor, size == null ? 100 : Math.min(size, 100));
            return page.items().stream().map(this::toPublicProduct).collect(Collectors.toList());
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> product(@PathVariable String storeId, @PathVariable Long id) {
        Long sid = sid(storeId);
        TenantContext.set(sid, null);
        try {
            var p = catalogService.getProduct(id);
            if (!"active".equalsIgnoreCase(p.status())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
            }
            return toPublicProduct(p);
        } finally {
            TenantContext.clear();
        }
    }

    /** Public product DTO: no cost, supplier, or inventory-policy internals. */
    private Map<String, Object> toPublicProduct(com.becho.saas.modules.catalog.web.CatalogDtos.ProductDto p) {
        List<Map<String, Object>> variants = p.variants().stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.id().toString());
            m.put("sku", v.sku());
            m.put("price", v.priceMinor());
            m.put("compareAt", v.compareAtMinor());
            m.put("option1", v.option1());
            m.put("option2", v.option2());
            m.put("option3", v.option3());
            m.put("stock", v.stock());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id().toString());
        m.put("title", p.title());
        m.put("slug", p.slug());
        m.put("status", "Active");
        m.put("description", p.description());
        m.put("vendor", p.vendor());
        m.put("productType", p.productType());
        m.put("images", p.images());
        m.put("videoUrl", p.videoUrl());
        m.put("variants", variants);
        m.put("collectionId", "");
        return m;
    }

    // ---------------------------------------------------------------- settings

    @GetMapping(value = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public String settings(@PathVariable String storeId) {
        Long sid = sid(storeId);
        String raw = settingsCache.getSettings(sid);
        if (raw == null || raw.isBlank()) {
            return "{\"storeId\":\"" + sid + "\",\"currency\":\"BDT\"}";
        }
        return publicSettings(raw, sid);
    }

    /**
     * Strips sensitive keys from settings JSON: Brevo API key, courier
     * credentials, SMS API key. Keeps display/shipping/checkout fields.
     */
    static String publicSettings(String raw, Long sid) {
        try {
            JsonNode root = MAPPER.readTree(raw);
            if (!root.isObject()) return "{\"storeId\":\"" + sid + "\",\"currency\":\"BDT\"}";
            ObjectNode out = (ObjectNode) root;
            out.remove("brevo");
            out.remove("emailSender");
            JsonNode couriers = out.get("couriers");
            if (couriers != null && couriers.isObject()) {
                couriers.forEach(c -> {
                    if (c.isObject()) ((ObjectNode) c).remove("creds");
                });
            }
            JsonNode notifications = out.path("notifications");
            if (notifications.isObject()) {
                JsonNode sms = notifications.path("sms");
                if (sms.isObject()) ((ObjectNode) sms).remove("apiKey");
            }
            return MAPPER.writeValueAsString(out);
        } catch (Exception e) {
            return "{\"storeId\":\"" + sid + "\",\"currency\":\"BDT\"}";
        }
    }

    // ---------------------------------------------------------------- theme

    @GetMapping(value = "/theme", produces = MediaType.APPLICATION_JSON_VALUE)
    public String theme(@PathVariable String storeId) {
        Long sid = sid(storeId);
        Theme theme = themes.findByStoreIdAndPublishedTrue(sid).orElse(null);
        if (theme == null || theme.getSettings() == null || theme.getSettings().isBlank()) {
            return "{\"storeId\":\"" + sid + "\",\"preset\":\"minimal\"}";
        }
        return theme.getSettings();
    }

    // ---------------------------------------------------------------- discounts

    @GetMapping("/discounts")
    public List<Map<String, Object>> discounts(@PathVariable String storeId) {
        Long sid = sid(storeId);
        return discounts.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(sid).stream()
                .filter(d -> "active".equals(d.getStatus()))
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId().toString());
                    m.put("code", d.getCode());
                    m.put("title", d.getTitle());
                    boolean isPercent = "percentage".equals(d.getDiscountType());
                    m.put("type", isPercent ? "%" : "$");
                    m.put("value", isPercent && d.getValuePercent() != null
                            ? d.getValuePercent().doubleValue()
                            : (d.getValueMinor() != null ? d.getValueMinor() : 0));
                    m.put("active", true);
                    return m;
                }).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- apps

    @GetMapping(value = "/apps", produces = MediaType.APPLICATION_JSON_VALUE)
    public String apps(@PathVariable String storeId) {
        Long sid = sid(storeId);
        return appsConfigs.findById(sid)
                .map(com.becho.saas.modules.platform.entity.StoreAppsConfig::getConfig)
                .orElse("{}");
    }

    // ---------------------------------------------------------------- collections

    @GetMapping("/collections")
    public List<Map<String, Object>> collections(@PathVariable String storeId) {
        Long sid = sid(storeId);
        return collections.findByStoreIdAndDeletedAtIsNullOrderByTitle(sid).stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId().toString());
            m.put("name", c.getTitle());
            m.put("description", c.getDescription());
            m.put("image", c.getImageUrl());
            m.put("sort", c.getSortOrder());
            m.put("productIds", jdbc.queryForList(
                    "SELECT product_id FROM collection_products WHERE collection_id = ? ORDER BY position",
                    Long.class, c.getId()).stream().map(String::valueOf).toList());
            return m;
        }).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- bundles (public)

    @GetMapping("/bundles")
    public List<Map<String, Object>> bundles(@PathVariable String storeId) {
        Long sid = sid(storeId);
        return bundles.findByStoreIdOrderByCreatedAtDesc(sid).stream()
                .filter(b -> b.isActive())
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId().toString());
                    m.put("name", b.getName());
                    m.put("items", b.getItems());
                    m.put("priceMinor", b.getPriceMinor());
                    m.put("image", b.getImage());
                    return m;
                }).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- posts/blog (public, published only)

    @GetMapping("/posts")
    public List<Map<String, Object>> posts(@PathVariable String storeId) {
        Long sid = sid(storeId);
        return pages.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(sid).stream()
                .filter(p -> "published".equals(p.getStatus()))
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId().toString());
                    m.put("title", p.getTitle());
                    m.put("slug", p.getSlug());
                    m.put("body", p.getBody());
                    return m;
                }).collect(Collectors.toList());
    }
}
