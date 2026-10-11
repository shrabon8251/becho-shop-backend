package com.becho.saas.storage;

import com.becho.saas.BaseIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Spring wiring for R2: the R2Config @Bean methods create
 * R2StorageService correctly, and without env vars the service reports
 * not-configured (so the controller returns clean 503).
 */
@Disabled("needs live MySQL — enable when DB is up")
class R2WiringTest extends BaseIntegrationTest {

    @Autowired R2StorageService storageService;
    @Autowired R2Properties properties;

    @Test
    void beansAreWired() {
        assertNotNull(storageService);
        assertNotNull(properties);
    }

    @Test
    void serviceReportsNotConfiguredWithoutEnvVars() {
        // test profile sets no R2_* env vars
        assertFalse(storageService.isConfigured());
    }

    @Test
    void buildKeyFormatIsCorrect() {
        String key = storageService.buildKey(42L, "photo.PNG");
        assertTrue(key.startsWith("stores/42/products/"));
        assertTrue(key.endsWith(".png"));
    }

    @Test
    void publicUrlJoinsCorrectly() {
        // publicUrl uses the configured R2Properties.publicUrl(); without env vars
        // it falls back gracefully — just verify it doesn't throw and joins the key
        String url = storageService.publicUrl("stores/1/products/a.png");
        assertTrue(url.endsWith("stores/1/products/a.png"));
    }
}
