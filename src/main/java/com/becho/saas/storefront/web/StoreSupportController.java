package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.platform.entity.SupportTicket;
import com.becho.saas.modules.platform.repository.SupportTicketRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Support ticket endpoints: GET/POST /stores/{storeId}/support/tickets,
 * POST /stores/{storeId}/support/tickets/{id}/reply { text }.
 * Frontend Ticket: { id, storeId, storeName, subject, message,
 *                    status: "Open"|"Closed", createdAt, replies[] }.
 */
@RestController
@RequestMapping("/stores/{storeId}/support/tickets")
public class StoreSupportController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SupportTicketRepository tickets;
  private final StoreRepository stores;
  private final StoreScope scope;

  public StoreSupportController(SupportTicketRepository tickets,
                                 StoreRepository stores, StoreScope scope) {
    this.tickets = tickets;
    this.stores = stores;
    this.scope = scope;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public List<Map<String, Object>> list(@PathVariable String storeId) {
    Long sid = scope.require(storeId);
    return tickets.findByStoreIdOrderByCreatedAtDesc(sid).stream()
        .map(t -> toFrontend(t, storeId)).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> create(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    SupportTicket t = new SupportTicket();
    t.setStore(store);
    t.setSubject(body.get("subject") != null
        ? String.valueOf(body.get("subject")) : "");
    t.setMessage(body.get("message") != null
        ? String.valueOf(body.get("message")) : "");
    t.setCustomerEmail(body.get("customerEmail") != null
        ? String.valueOf(body.get("customerEmail")) : null);
    t.setReplies("[]");
    return toFrontend(tickets.save(t), storeId);
  }

  @PostMapping("/{id}/reply")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  public Map<String, Object> reply(@PathVariable String storeId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    SupportTicket t = tickets.findByIdAndStoreId(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Ticket"));
    List<Map<String, Object>> replies = readReplies(t.getReplies());
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("from", "merchant");
    r.put("text", body.get("text") != null ? String.valueOf(body.get("text")) : "");
    r.put("at", System.currentTimeMillis());
    replies.add(r);
    t.setReplies(writeReplies(replies));
    return toFrontend(tickets.save(t), storeId);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> readReplies(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    try {
      return MAPPER.readValue(json, List.class);
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private String writeReplies(List<Map<String, Object>> replies) {
    try {
      return MAPPER.writeValueAsString(replies);
    } catch (Exception e) {
      return "[]";
    }
  }

  private Map<String, Object> toFrontend(SupportTicket t, String storeId) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId().toString());
    m.put("storeId", storeId);
    m.put("storeName", t.getStore() != null ? t.getStore().getName() : "");
    m.put("subject", t.getSubject());
    m.put("message", t.getMessage());
    m.put("status", "closed".equalsIgnoreCase(t.getStatus()) ? "Closed" : "Open");
    m.put("createdAt", t.getCreatedAt().atZone(ZoneOffset.UTC).toInstant().toEpochMilli());
    m.put("replies", readReplies(t.getReplies()));
    return m;
  }
}
