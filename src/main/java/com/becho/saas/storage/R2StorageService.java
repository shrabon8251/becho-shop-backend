package com.becho.saas.storage;

import com.becho.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Uploads product images to Cloudflare R2 and returns their public CDN URLs.
 *
 * <p>Object key scheme: {@code stores/{storeId}/products/{uuid}.{ext}} — the
 * storeId prefix keeps every tenant's objects isolated.
 */
@Service
public class R2StorageService {

  private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);

  private final R2Properties props;
  private final S3Client s3;

  public R2StorageService(R2Properties props, S3Client s3) {
    this.props = props;
    this.s3 = s3;
  }

  public boolean isConfigured() {
    return props.isConfigured();
  }

  /**
   * Uploads a product image to R2 and returns its public URL.
   *
   * @throws ApiException 503 when R2 is not configured or unreachable (never leaks internals)
   */
  public String uploadProductImage(Long storeId, String originalFilename,
                                   String contentType, InputStream data, long size) {
    if (!props.isConfigured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED",
          "Image storage is not configured");
    }
    String key = buildKey(storeId, originalFilename);
    try {
      PutObjectRequest req = PutObjectRequest.builder()
          .bucket(props.getBucket())
          .key(key)
          .contentType(contentType)
          .contentLength(size)
          .build();
      s3.putObject(req, RequestBody.fromInputStream(data, size));
    } catch (S3Exception | SdkClientException e) {
      log.warn("R2 upload failed (bucket={}, key={})", props.getBucket(), key, e);
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
          "Image storage is temporarily unavailable");
    }
    return publicUrl(key);
  }

  /** {@code stores/{storeId}/products/{uuid}.{ext}} — ext is sanitized, uuid is random. */
  public static String buildKey(Long storeId, String originalFilename) {
    return "stores/" + storeId + "/products/" + UUID.randomUUID() + sanitizeExtension(originalFilename);
  }

  /** Joins the configured public base URL with an object key. */
  public String publicUrl(String key) {
    String base = props.getPublicUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/" + key;
  }

  /**
   * Keeps only a safe, lowercase extension (e.g. ".png"). Anything else —
   * path tricks, double extensions like ".php", missing names — becomes "".
   */
  static String sanitizeExtension(String filename) {
    if (filename == null) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (!ext.matches("[a-z0-9]{1,8}")) {
      return "";
    }
    return "." + ext;
  }
}
