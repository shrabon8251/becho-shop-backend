package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.Webhook;
import com.becho.saas.modules.platform.repository.WebhookRepository;
import com.becho.saas.modules.platform.service.WebhookDispatchService;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible webhook endpoints: /stores/{storeId}/webhooks[/{id}],
 * plus POST /stores/{storeId}/webhooks/{id}/ping which fires a test event.
 *
 * Security: the signing secret is shown ONCE at creation; only its SHA-256
 * hash is persisted (see WebhookDispatchService for the HMAC protocol).
 */
@RestController
@RequestMapping("/stores/{storeId}/webhooks")
public class StoreWebhookController {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final WebhookRepository webhooks;
  private final WebhookDispatchService dispatchService;
  private final StoreRepository storeRepository;
  private final StoreScope scope;

  public StoreWebhookController(WebhookRepository webhooks,
                                 WebhookDispatchService dispatchService,
                                 StoreRepository storeRepository, StoreScope scope) {
    this.webhooks = webhooks;
    this.dispatchService = dispatchService;
    this.storeRepository = storeRepository;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return webhooks.findByStoreIdOrderByCreatedAtDesc(sid)
        .stream().map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Webhook w = new Webhook();
    w.setStore(storeRepository.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    applyBody(w, body);
    String rawSecret = generateSecret();
    w.setSecretHash(sha256(rawSecret));
    w.setStatus("active");
    Webhook saved = webhooks.save(w);
    // Raw secret is shown ONCE — it is never persisted and cannot be recovered.
    Map<String, Object> m = toFrontend(saved);
    m.put("secret", rawSecret);
    return m;
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    if (StoreScope.isNewId(id)) return create(storeId, body);
    Webhook w = findInStore(sid, id);
    applyBody(w, body);
    return toFrontend(webhooks.save(w));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Webhook w = findInStore(sid, id);
    w.setStatus("disabled");
    webhooks.save(w);
  }

  /**
   * Sends a test event to the webhook's URL. Delivery runs async via
   * WebhookDispatchService (HMAC-signed); failures are counted and the
   * webhook auto-disables after repeated failures.
   */
  @PostMapping("/{id}/ping")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public void ping(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Webhook w = findInStore(sid, id);
    String payload = "{\"event\":\"ping\",\"webhookId\":\"" + w.getId()
        + "\",\"storeId\":\"" + sid + "\",\"at\":" + System.currentTimeMillis() + "}";
    dispatchService.emit(sid, w.getTopic(), payload);
  }

  private Webhook findInStore(Long sid, String id) {
    return webhooks.findById(StoreScope.lid(id))
        .filter(w -> w.getStore() != null && sid.equals(w.getStore().getId()))
        .orElseThrow(() -> ApiException.notFound("Webhook"));
  }

  private void applyBody(Webhook w, Map<String, Object> body) {
    // Frontend sends {event, url, active}; accept both naming styles.
    if (body.get("event") != null) w.setTopic((String) body.get("event"));
    if (body.get("topic") != null) w.setTopic((String) body.get("topic"));
    if (body.get("url") != null) {
      com.becho.saas.common.SsrfGuard.validateUrl((String) body.get("url"));
      w.setTargetUrl((String) body.get("url"));
    }
    if (body.get("targetUrl") != null) {
      com.becho.saas.common.SsrfGuard.validateUrl((String) body.get("targetUrl"));
      w.setTargetUrl((String) body.get("targetUrl"));
    }
    Object active = body.get("active");
    if (active instanceof Boolean b) w.setStatus(b ? "active" : "disabled");
  }

  private Map<String, Object> toFrontend(Webhook w) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", w.getId().toString());
    m.put("event", w.getTopic());
    m.put("url", w.getTargetUrl());
    m.put("active", "active".equals(w.getStatus()));
    return m;
  }

  private static String generateSecret() {
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    return "whsec_" + HexFormat.of().formatHex(raw);
  }

  private static String sha256(String s) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
