package com.becho.saas.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the R2 object-key scheme and public-URL joining without
 * touching the network.
 */
class R2StorageServiceTest {

  private R2StorageService serviceWithPublicUrl(String publicUrl) {
    R2Properties props = new R2Properties();
    props.setPublicUrl(publicUrl);
    return new R2StorageService(props, null);
  }

  @Test
  void buildKeyHasTenantIsolatedScheme() {
    String key = R2StorageService.buildKey(42L, "photo.png");
    assertTrue(key.matches("^stores/42/products/[0-9a-f-]{36}\\.png$"), "unexpected key: " + key);
  }

  @Test
  void buildKeySanitizesExtension() {
    assertTrue(R2StorageService.buildKey(1L, "PHOTO.JPG").endsWith(".jpg"));
    // Path traversal / weird names collapse to no extension.
    assertTrue(R2StorageService.buildKey(1L, "../../etc/passwd").matches("^stores/1/products/[0-9a-f-]{36}$"));
    assertTrue(R2StorageService.buildKey(1L, "noext").matches("^stores/1/products/[0-9a-f-]{36}$"));
    assertTrue(R2StorageService.buildKey(1L, null).matches("^stores/1/products/[0-9a-f-]{36}$"));
    assertTrue(R2StorageService.buildKey(1L, "shell.php").endsWith(".php"));
  }

  @Test
  void publicUrlJoinsBaseAndKey() {
    R2StorageService s = serviceWithPublicUrl("https://pub-abc123.r2.dev");
    assertEquals("https://pub-abc123.r2.dev/stores/1/products/x.png",
        s.publicUrl("stores/1/products/x.png"));
  }

  @Test
  void publicUrlToleratesTrailingSlash() {
    R2StorageService s = serviceWithPublicUrl("https://cdn.becho.com/");
    assertEquals("https://cdn.becho.com/stores/2/products/y.jpg",
        s.publicUrl("stores/2/products/y.jpg"));
  }

  @Test
  void isConfiguredRequiresEveryValue() {
    R2Properties props = new R2Properties();
    assertFalse(props.isConfigured());
    props.setAccountId("acc");
    props.setAccessKeyId("key");
    props.setSecretAccessKey("secret");
    props.setBucket("becho-uploads");
    assertFalse(props.isConfigured(), "public URL still missing");
    props.setPublicUrl("https://pub-abc123.r2.dev");
    assertTrue(props.isConfigured());
  }
}
