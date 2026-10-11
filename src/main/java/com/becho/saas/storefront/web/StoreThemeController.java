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

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public String get(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        Theme theme = themes.findByStoreIdAndPublishedTrue(sid).orElse(null);
        if (theme == null || theme.getSettings() == null || theme.getSettings().isBlank()) {
            return "{\"storeId\":\"" + sid + "\",\"preset\":\"minimal\"}";
        }
        return theme.getSettings();
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public String save(@PathVariable String storeId, @RequestBody String rawJson) {
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
        Theme theme = themes.findByStoreIdAndPublishedTrue(sid).orElse(null);
        if (theme == null) {
            theme = new Theme();
            theme.setStore(storeRepository.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
            theme.setName("Default");
            theme.setPublished(true);
        }
        theme.setSettings(rawJson);
        themes.save(theme);
        return rawJson;
    }
}
