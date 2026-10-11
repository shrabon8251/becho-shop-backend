package com.becho.saas.storage;

import com.becho.saas.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Set;

/**
 * Validates uploaded images before they reach storage.
 *
 * <p>Why all three checks:
 * <ul>
 *   <li>The multipart {@code Content-Type} header is fully client-controlled —
 *       it proves nothing.</li>
 *   <li>The filename extension decides the served URL suffix; only a strict
 *       allowlist is accepted. SVG is rejected outright because it can carry
 *       embedded scripts (stored XSS).</li>
 *   <li>Magic-byte sniffing verifies the actual bytes match the claimed image
 *       format, and the extension must agree with the sniffed format (a PHP
 *       payload renamed to {@code .png} with a fake {@code image/png} header
 *       is rejected).</li>
 * </ul>
 */
public final class ImageUploadValidator {

  private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp", "gif");

  private ImageUploadValidator() {}

  public record ValidatedImage(String extension, String contentType) {}

  /**
   * Validates {@code filename} + raw {@code bytes}.
   *
   * @return the safe extension (with leading dot) and canonical MIME type
   * @throws ApiException 400 when the file is not an allowed image
   */
  public static ValidatedImage validate(String filename, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw bad("EMPTY_FILE", "No file uploaded");
    }
    String ext = extensionOf(filename);
    if (ext.isEmpty() || !ALLOWED.contains(ext)) {
      throw bad("INVALID_TYPE", "Only jpg, jpeg, png, webp and gif images are allowed");
    }
    String detected = sniff(bytes);
    if (detected == null) {
      throw bad("INVALID_IMAGE", "File content is not a valid image");
    }
    if (!extensionMatches(ext, detected)) {
      throw bad("TYPE_MISMATCH", "File extension does not match image content");
    }
    return new ValidatedImage("." + ext, "image/" + detected);
  }

  /** Lowercase extension without the dot, or "" when unusable. */
  static String extensionOf(String filename) {
    if (filename == null) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static boolean extensionMatches(String ext, String detected) {
    return switch (detected) {
      case "jpeg" -> ext.equals("jpg") || ext.equals("jpeg");
      default -> ext.equals(detected);
    };
  }

  /**
   * Returns "jpeg", "png", "gif" or "webp" when the leading bytes match a
   * known image signature, otherwise null.
   */
  static String sniff(byte[] b) {
    if (b.length >= 3
        && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
      return "jpeg";
    }
    if (b.length >= 8
        && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
        && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
      return "png";
    }
    if (b.length >= 6
        && b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x38
        && (b[4] == 0x37 || b[4] == 0x39) && b[5] == 0x61) {
      return "gif";
    }
    if (b.length >= 12
        && b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
        && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50) {
      return "webp";
    }
    return null;
  }

  private static ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }
}
