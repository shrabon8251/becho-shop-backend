package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.settings.SettingsCryptoService;
import com.becho.saas.modules.settings.StoreSettingsCache;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Frontend-compatible settings endpoints: GET/PUT /stores/{storeId}/settings.
 * Stores the raw JSON in the store's settings column.
 */
@RestController
@RequestMapping("/stores/{storeId}/settings")
public class StoreSettingsController {

    private final StoreRepository stores;
    private final StoreScope scope;
    private final SettingsCryptoService crypto;
    private final StoreSettingsCache settingsCache;

    public StoreSettingsController(StoreRepository stores, StoreScope scope,
                                   SettingsCryptoService crypto,
                                   StoreSettingsCache settingsCache) {
        this.stores = stores;
        this.scope = scope;
        this.crypto = crypto;
        this.settingsCache = settingsCache;
    }

    private static final int MAX_SETTINGS_BYTES = 1024 * 1024; // 1 MB — settings are tens of KB
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public String get(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        String settings = settingsCache.getSettings(sid);
        if (settings == null || settings.isBlank()) {
            return "{\"storeId\":\"" + sid + "\",\"currency\":\"BDT\"}";
        }
        return settings;
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public String save(@PathVariable String storeId, @RequestBody String rawJson) {
        Long sid = scope.require(storeId);
        if (rawJson == null || rawJson.isBlank()) {
            throw ApiException.badRequest("EMPTY_SETTINGS", "Settings JSON is required");
        }
        if (rawJson.length() > MAX_SETTINGS_BYTES) {
            throw ApiException.badRequest("SETTINGS_TOO_LARGE", "Settings JSON must be 1MB or smaller");
        }
        try {
            MAPPER.readTree(rawJson);
        } catch (JacksonException e) {
            throw ApiException.badRequest("INVALID_SETTINGS_JSON", "Settings must be valid JSON");
        }
        Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
        // Encrypt courier API keys / SMS keys before persisting.
        String encrypted = crypto.encryptSensitive(rawJson);
        store.setSettings(encrypted);
        stores.save(store);
        settingsCache.evict(sid);
        return rawJson;
    }
}
