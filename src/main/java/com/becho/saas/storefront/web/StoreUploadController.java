package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.storage.ImageUploadValidator;
import com.becho.saas.storage.R2StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Frontend-compatible upload endpoint: POST /stores/{storeId}/uploads.
 * Accepts a multipart image (max 5MB) and stores it in Cloudflare R2,
 * returning the public CDN URL as {url}.
 *
 * <p>Validation is defense-in-depth: the extension allowlist rejects SVG
 * outright (stored XSS), and magic-byte sniffing rejects fake images whose
 * bytes don't match an image format — the client-supplied Content-Type is
 * never trusted.
 */
@RestController
@RequestMapping("/stores/{storeId}/uploads")
public class StoreUploadController {

  private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB

  private final StoreScope scope;
  private final R2StorageService storage;

  public StoreUploadController(StoreScope scope, R2StorageService storage) {
    this.scope = scope;
    this.storage = storage;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> upload(@PathVariable String storeId,
                                     @RequestParam("file") MultipartFile file) {
    Long sid = scope.require(storeId);
    if (file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "No file uploaded");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Image must be 5MB or smaller");
    }
    try {
      byte[] bytes = file.getBytes();
      ImageUploadValidator.ValidatedImage img =
          ImageUploadValidator.validate(file.getOriginalFilename(), bytes);
      String url = storage.uploadProductImage(sid, "upload" + img.extension(),
          img.contentType(), new ByteArrayInputStream(bytes), bytes.length);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("url", url);
      return m;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      // Never leak internal details to the client.
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "Could not store file");
    }
  }
}
