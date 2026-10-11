package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.auth.service.MailService;
import com.becho.saas.modules.settings.StoreSettingsCache;
import com.becho.saas.modules.catalog.entity.Product;
import com.becho.saas.modules.catalog.entity.StockAlert;
import com.becho.saas.modules.catalog.repository.ProductRepository;
import com.becho.saas.modules.catalog.repository.StockAlertRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Back in Stock" alerts.
 *
 * <ul>
 *   <li>{@code POST /stores/{storeId}/stock-alerts} — public; a shopper
 *       subscribes with an email. Idempotent per (product, email).</li>
 *   <li>{@code GET /stores/{storeId}/stock-alerts} — merchant lists alerts.</li>
 *   <li>{@code POST /stores/{storeId}/stock-alerts/notify} — merchant triggers
 *       notification emails for a restocked product; returns the sent count.</li>
 * </ul>
 *
 * <p>Email sending is best-effort via {@link MailService} (SMTP from env,
 * never hardcoded). Failures are logged, not thrown — notify still marks
 * rows so one bad address can't wedge the queue.
 */
@RestController
@RequestMapping("/stores/{storeId}/stock-alerts")
public class StoreStockAlertController {

  private static final Logger log = LoggerFactory.getLogger(StoreStockAlertController.class);

  private final StockAlertRepository alerts;
  private final ProductRepository products;
  private final MailService mail;
  private final StoreScope scope;
  private final com.becho.saas.modules.tenancy.repository.StoreRepository stores;
  private final StoreSettingsCache settingsCache;

  public StoreStockAlertController(StockAlertRepository alerts,
                                   ProductRepository products,
                                   MailService mail,
                                   StoreScope scope,
                                   com.becho.saas.modules.tenancy.repository.StoreRepository stores,
                                   StoreSettingsCache settingsCache) {
    this.alerts = alerts;
    this.products = products;
    this.mail = mail;
    this.scope = scope;
    this.stores = stores;
    this.settingsCache = settingsCache;
  }

  public record SubscribeRequest(
      @NotNull Long productId,
      @NotBlank @Email @Size(max = 255) String email) {}

  public record NotifyRequest(
      @NotNull Long productId) {}

  /** Public: shopper subscribes. Idempotent — re-subscribing returns the existing row. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> subscribe(@PathVariable String storeId,
                                       @Valid @RequestBody SubscribeRequest req) {
    Long sid = StoreScope.lid(storeId);
    String email = req.email().trim().toLowerCase();
    Product product = products.findByIdAndStoreIdAndDeletedAtIsNull(req.productId(), sid)
        .orElseThrow(() -> ApiException.notFound("Product"));

    StockAlert existing = alerts.findByStoreIdAndProductIdAndEmail(sid, product.getId(), email);
    if (existing != null) {
      return toFrontend(existing);
    }
    StockAlert a = new StockAlert();
    a.setStoreId(sid);
    a.setProduct(product);
    a.setEmail(email);
    alerts.save(a);
    return toFrontend(a);
  }

  /** Merchant: list all alerts for the store. */
  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return alerts.findByStoreIdAndNotifiedFalse(sid).stream()
        .map(this::toFrontend)
        .toList();
  }

  /**
   * Merchant: notify all pending subscribers that the product is back in
   * stock. Returns {@code { sent }}. Marks rows notified even if individual
   * sends fail (best-effort, like the rest of the mail pipeline).
   */
  @PostMapping("/notify")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  @Transactional
  public Map<String, Object> notify(@PathVariable String storeId,
                                    @Valid @RequestBody NotifyRequest req) {
    Long sid = scope.require(storeId);
    Product product = products.findByIdAndStoreIdAndDeletedAtIsNull(req.productId(), sid)
        .orElseThrow(() -> ApiException.notFound("Product"));

    List<StockAlert> pending = alerts.findByStoreIdAndProductIdAndNotifiedFalse(sid, product.getId());
    String storeSettings = settingsCache.getSettings(sid);
    int sent = 0;
    for (StockAlert a : pending) {
      try {
        mail.sendStockAlert(a.getEmail(), product.getTitle(), storeSettings);
        sent++;
      } catch (Exception e) {
        log.warn("Stock alert email failed for {}: {}", a.getEmail(), e.toString());
      }
      a.setNotified(true);
    }
    alerts.saveAll(pending);
    return Map.of("sent", sent, "productId", product.getId().toString());
  }

  private Map<String, Object> toFrontend(StockAlert a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId().toString());
    m.put("productId", a.getProduct() != null ? a.getProduct().getId().toString() : null);
    m.put("email", a.getEmail());
    m.put("createdAt", a.getCreatedAt() == null ? 0
        : a.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
    m.put("notified", a.isNotified());
    return m;
  }
}
