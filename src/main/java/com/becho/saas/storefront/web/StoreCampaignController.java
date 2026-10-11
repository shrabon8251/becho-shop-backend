package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.marketing.entity.Campaign;
import com.becho.saas.modules.marketing.repository.CampaignRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible campaign endpoints: /stores/{storeId}/campaigns[/{id}].
 * DB-backed. Frontend Campaign: { id, name, channel, budget, spent, active, ... }.
 */
@RestController
@RequestMapping("/stores/{storeId}/campaigns")
public class StoreCampaignController {

  private final CampaignRepository campaigns;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreCampaignController(CampaignRepository campaigns, StoreRepository stores, StoreScope scope) {
    this.campaigns = campaigns;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return campaigns.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(this::toFrontend).collect(Collectors.toList());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Campaign c = new Campaign();
    c.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    applyBody(c, body);
    return toFrontend(campaigns.save(c));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> save(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Campaign c;
    if (StoreScope.isNewId(id)) {
      c = new Campaign();
      c.setStore(stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
    } else {
      c = campaigns.findByIdAndStoreId(StoreScope.lid(id), sid)
          .orElseThrow(() -> ApiException.notFound("Campaign"));
    }
    applyBody(c, body);
    return toFrontend(campaigns.save(c));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void remove(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Campaign c = campaigns.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Campaign"));
    campaigns.delete(c);
  }

  private void applyBody(Campaign c, Map<String, Object> body) {
    if (body.get("name") != null) c.setName(String.valueOf(body.get("name")));
    if (body.get("channel") != null) c.setChannel(mapChannel(String.valueOf(body.get("channel"))));
    if (body.get("status") != null) c.setStatus(mapStatus(String.valueOf(body.get("status"))));
    if (body.get("subject") != null) c.setSubject(String.valueOf(body.get("subject")));
    if (body.get("body") != null) c.setBody(String.valueOf(body.get("body")));
    if (body.get("audience") != null) c.setAudience(String.valueOf(body.get("audience")));
    if (body.get("scheduledAt") instanceof Number n)
      c.setScheduledAt(LocalDateTime.ofEpochSecond(n.longValue() / 1000, 0, ZoneOffset.UTC));
    if (body.get("sentAt") instanceof Number n)
      c.setSentAt(LocalDateTime.ofEpochSecond(n.longValue() / 1000, 0, ZoneOffset.UTC));
    // active=false maps to cancelled/draft; active=true keeps current status
    if (Boolean.FALSE.equals(body.get("active")) && "draft".equals(c.getStatus())) {
      c.setStatus("cancelled");
    }
  }

  private String mapChannel(String frontend) {
    return switch (frontend) {
      case "Email", "email" -> "email";
      case "Social", "Google Ads" -> "email"; // DB only supports email|sms
      case "sms", "SMS" -> "sms";
      default -> "email";
    };
  }

  private String mapStatus(String frontend) {
    return switch (frontend) {
      case "Draft", "draft" -> "draft";
      case "Scheduled", "scheduled" -> "scheduled";
      case "Sent", "sent" -> "sent";
      default -> "draft";
    };
  }

  private Map<String, Object> toFrontend(Campaign c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId().toString());
    m.put("name", c.getName());
    m.put("channel", "sms".equals(c.getChannel()) ? "SMS" : "Email");
    m.put("budget", 0);
    m.put("spent", 0);
    m.put("active", !"cancelled".equals(c.getStatus()));
    m.put("status", switch (c.getStatus()) {
      case "scheduled" -> "Scheduled";
      case "sent" -> "Sent";
      default -> "Draft";
    });
    if (c.getSubject() != null) m.put("subject", c.getSubject());
    if (c.getBody() != null) m.put("body", c.getBody());
    if (c.getAudience() != null) m.put("audience", c.getAudience());
    if (c.getScheduledAt() != null)
      m.put("scheduledAt", c.getScheduledAt().toEpochSecond(ZoneOffset.UTC) * 1000);
    if (c.getSentAt() != null)
      m.put("sentAt", c.getSentAt().toEpochSecond(ZoneOffset.UTC) * 1000);
    m.put("createdAt", c.getCreatedAt().toEpochSecond(ZoneOffset.UTC) * 1000);
    return m;
  }
}
