package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.EmailTemplate;
import com.becho.saas.modules.platform.repository.EmailTemplateRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible email templates:
 * GET /stores/{storeId}/email-templates,
 * PUT /stores/{storeId}/email-templates/{key}.
 * Shape: { key, subject, body, enabled }.
 */
@RestController
@RequestMapping("/stores/{storeId}/email-templates")
public class StoreEmailTemplateController {

  /** Keys the frontend knows about. */
  private static final List<String> KNOWN_KEYS =
      List.of("order_confirmation", "order_shipped", "order_refunded");

  private static final Map<String, String[]> DEFAULTS = Map.of(
      "order_confirmation", new String[]{
          "Order #{{order_id}} confirmed",
          "Hi {{customer_name}},\n\nThank you for your order #{{order_id}} at {{store_name}}.",
          "true"},
      "order_shipped", new String[]{
          "Your order #{{order_id}} is on its way",
          "Hi {{customer_name}},\n\nYour order has been fulfilled.",
          "true"},
      "order_refunded", new String[]{
          "Refund for order #{{order_id}}",
          "Hi {{customer_name}},\n\nWe have refunded order #{{order_id}}.",
          "false"});

  private final EmailTemplateRepository templates;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreEmailTemplateController(EmailTemplateRepository templates, StoreRepository stores,
                                      StoreScope scope) {
    this.templates = templates;
    this.stores = stores;
    this.scope = scope;
  }

  public record TemplateBody(
      @NotBlank @Size(max = 255) String subject,
      @NotBlank @Size(max = 20000) String body,
      Boolean enabled) {}

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  @Transactional
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    Map<String, EmailTemplate> stored = templates.findByStoreId(sid).stream()
        .collect(Collectors.toMap(EmailTemplate::getKey, t -> t));
    // Lazy-seed defaults so GET always reflects real rows.
    for (String key : KNOWN_KEYS) {
      if (!stored.containsKey(key)) {
        EmailTemplate t = new EmailTemplate();
        t.setStore(store);
        t.setKey(key);
        String[] d = DEFAULTS.get(key);
        t.setSubject(d[0]);
        t.setBody(d[1]);
        t.setEnabled(Boolean.parseBoolean(d[2]));
        templates.save(t);
        stored.put(key, t);
      }
    }
    return KNOWN_KEYS.stream().map(k -> toFrontend(stored.get(k))).toList();
  }

  @PutMapping("/{key}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  @Transactional
  public Map<String, Object> save(@PathVariable String storeId,
                                  @PathVariable String key,
                                  @Valid @RequestBody TemplateBody body) {
    Long sid = scope.require(storeId);
    if (!KNOWN_KEYS.contains(key)) {
      throw ApiException.badRequest("UNKNOWN_TEMPLATE", "Unknown template: " + key);
    }
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    EmailTemplate t = templates.findById(new EmailTemplate.EmailTemplateId(sid, key))
        .orElseGet(() -> {
          EmailTemplate nt = new EmailTemplate();
          nt.setStore(store);
          nt.setKey(key);
          return nt;
        });
    t.setSubject(body.subject().trim());
    t.setBody(body.body());
    if (body.enabled() != null) {
      t.setEnabled(body.enabled());
    }
    templates.save(t);
    // Sync to settings JSON so NotificationService actually uses the template.
    // (NotificationService reads from settings.notifications.templates, not the DB table.)
    syncToSettings(store, key, t.getSubject(), t.getBody());
    return toFrontend(t);
  }

  /** Mirror the DB template into settings.notifications.templates.{key}. */
  private void syncToSettings(Store store, String key, String subject, String body) {
    try {
      tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
      tools.jackson.databind.node.ObjectNode root = store.getSettings() == null || store.getSettings().isBlank()
          ? mapper.createObjectNode()
          : (tools.jackson.databind.node.ObjectNode) mapper.readTree(store.getSettings());
      tools.jackson.databind.node.ObjectNode notif = root.withObject("notifications");
      tools.jackson.databind.node.ObjectNode tmpls = notif.withObject("templates");
      tools.jackson.databind.node.ObjectNode tpl = tmpls.withObject(key);
      tpl.put("subject", subject);
      tpl.put("emailBody", body);
      store.setSettings(mapper.writeValueAsString(root));
      stores.save(store);
    } catch (Exception e) {
      // Settings sync is best-effort; the DB template is the source of truth.
    }
  }

  private Map<String, Object> toFrontend(EmailTemplate t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", t.getKey());
    m.put("subject", t.getSubject());
    m.put("body", t.getBody());
    m.put("enabled", t.isEnabled());
    return m;
  }
}
