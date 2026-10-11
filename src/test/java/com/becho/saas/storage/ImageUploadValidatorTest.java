package com.becho.saas.storage;

import com.becho.saas.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Upload validation: extension allowlist + magic-byte sniffing.
 * The client-supplied Content-Type is never trusted.
 */
class ImageUploadValidatorTest {

  private static final byte[] PNG = {
      (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
  private static final byte[] JPEG = {
      (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
  private static final byte[] GIF = {
      0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00};
  private static final byte[] WEBP = {
      0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
      0x57, 0x45, 0x42, 0x50, 0x00};
  private static final byte[] SVG =
      "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
  private static final byte[] PHP =
      "<?php echo shell_exec($_GET['cmd']); ?>".getBytes();

  @Test
  void acceptsValidImages() {
    assertEquals(".png", ImageUploadValidator.validate("photo.png", PNG).extension());
    assertEquals(".jpg", ImageUploadValidator.validate("photo.jpg", JPEG).extension());
    assertEquals(".jpeg", ImageUploadValidator.validate("photo.JPEG", JPEG).extension());
    assertEquals(".gif", ImageUploadValidator.validate("anim.gif", GIF).extension());
    assertEquals(".webp", ImageUploadValidator.validate("img.webp", WEBP).extension());
  }

  @Test
  void canonicalContentTypeComesFromSniffingNotClient() {
    assertEquals("image/png", ImageUploadValidator.validate("x.png", PNG).contentType());
    assertEquals("image/jpeg", ImageUploadValidator.validate("x.jpg", JPEG).contentType());
  }

  @Test
  void rejectsSvgOutright() {
    ApiException e = assertThrows(ApiException.class,
        () -> ImageUploadValidator.validate("evil.svg", SVG));
    assertEquals("INVALID_TYPE", e.getCode());
  }

  @Test
  void rejectsFakeImageWithImageContentType() {
    // PHP payload with a .png name but non-image bytes -> rejected.
    ApiException e = assertThrows(ApiException.class,
        () -> ImageUploadValidator.validate("shell.png", PHP));
    assertEquals("INVALID_IMAGE", e.getCode());
  }

  @Test
  void rejectsMismatchedExtensionAndContent() {
    // Real JPEG bytes named .png -> rejected.
    ApiException e = assertThrows(ApiException.class,
        () -> ImageUploadValidator.validate("photo.png", JPEG));
    assertEquals("TYPE_MISMATCH", e.getCode());
  }

  @Test
  void rejectsMissingOrWeirdExtensions() {
    assertThrows(ApiException.class, () -> ImageUploadValidator.validate("noext", PNG));
    assertThrows(ApiException.class, () -> ImageUploadValidator.validate("x.php.png", PHP));
    assertThrows(ApiException.class, () -> ImageUploadValidator.validate(null, PNG));
  }

  @Test
  void rejectsEmptyFile() {
    assertThrows(ApiException.class, () -> ImageUploadValidator.validate("x.png", new byte[0]));
  }
}
