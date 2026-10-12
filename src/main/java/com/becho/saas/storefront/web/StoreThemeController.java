package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.Theme;
import com.becho.saas.modules.platform.repository.ThemeRepository;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Frontend-compatible theme endpoints: GET/PUT /stores/{storeId}/theme.
 * Stores the raw JSON in the theme's settings column.
 *
 * Per-preset settings: every theme-gallery preset keeps its own theme row,
 * keyed by presetKey. Switching presets in the builder loads that preset's
 * saved settings instead of the factory default, so customizing one preset
 * never wipes another's. The published row is the live storefront theme.
 */
@RestController
@RequestMapping("/stores/{storeId}/theme")
public class StoreThemeController {

    private final ThemeRepository themes;
    private final StoreRepository storeRepository;
    private final StoreScope scope;

    public StoreThemeController(ThemeRepository themes, StoreRepository storeRepository, StoreScope scope) {
        this.themes = themes;
        this.storeRepository = storeRepository;
        this.scope = scope;
    }

    private static final int MAX_THEME_BYTES = 1024 * 1024; // 1 MB — themes are tens of KB
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String keyOf(String presetKey) {
        return (presetKey != null && !presetKey.isBlank()) ? presetKey : null;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public String get(@PathVariable String storeId,
                      @RequestParam(value = "presetKey", required = false) String presetKey) {
        Long sid = scope.require(storeId);
        String key = keyOf(presetKey);
        Theme theme = key != null
                ? themes.findByStoreIdAndPresetKey(sid, key).orElse(null)
                : themes.findByStoreIdAndPublishedTrue(sid).orElse(null);
        if (theme == null || theme.getSettings() == null || theme.getSettings().isBlank()) {
            // This preset was never customized: tell the frontend which factory
            // preset to start from (its normalizer resolves it to the real theme).
            return "{\"storeId\":\"" + sid + "\",\"preset\":\"" + (key != null ? key : "minimal") + "\"}";
        }
        return theme.getSettings();
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public String save(@PathVariable String storeId,
                       @RequestParam(value = "presetKey", required = false) String presetKey,
                       @RequestBody String rawJson) {
        Long sid = scope.require(storeId);
        if (rawJson == null || rawJson.isBlank()) {
            throw ApiException.badRequest("EMPTY_THEME", "Theme JSON is required");
        }
        if (rawJson.length() > MAX_THEME_BYTES) {
            throw ApiException.badRequest("THEME_TOO_LARGE", "Theme JSON must be 1MB or smaller");
        }
        try {
            MAPPER.readTree(rawJson);
        } catch (JacksonException e) {
            throw ApiException.badRequest("INVALID_THEME_JSON", "Theme must be valid JSON");
        }
        String key = keyOf(presetKey);
        Theme theme = key != null ? themes.findByStoreIdAndPresetKey(sid, key).orElse(null) : null;
        if (theme == null) {
            theme = themes.findByStoreIdAndPublishedTrue(sid).orElse(null);
            // Adopt the legacy unkeyed row (saved before per-preset themes existed)
            // for this preset instead of leaving two competing rows behind.
            // A row already keyed to a *different* preset is never reused.
            if (theme != null && theme.getPresetKey() != null) theme = null;
        }
        if (theme == null) {
            theme = new Theme();
            theme.setStore(storeRepository.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
            theme.setName("Default");
        }
        if (key != null) theme.setPresetKey(key);
        theme.setSettings(rawJson);
        theme.setPublished(true);
        themes.save(theme);
        // Exactly one published (live) theme per store.
        for (Theme other : themes.findByStoreId(sid)) {
            if (!other.getId().equals(theme.getId()) && other.isPublished()) {
                other.setPublished(false);
                themes.save(other);
            }
        }
        return rawJson;
    }
}
