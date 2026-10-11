package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Platform admin endpoints: /platform/stores, /platform/plans, /platform/tickets,
 * /platform/announcements, /platform/stores/{id}/impersonate.
 * All require platform admin role (enforced via @PreAuthorize).
 */
@RestController
@RequestMapping("/platform")
public class PlatformController {

    private final StoreRepository stores;

    public PlatformController(StoreRepository stores) {
        this.stores = stores;
    }

    @GetMapping("/stores")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<Map<String, Object>> listStores() {
        return stores.findAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId().toString());
            m.put("name", s.getName());
            m.put("sub", s.getSubdomain());
            m.put("status", "suspended".equalsIgnoreCase(s.getStatus()) ? "Suspended" : "Active");
            m.put("plan", s.getPlanName() != null ? s.getPlanName().toLowerCase() : "starter");
            m.put("gmv", 0); // TODO(post-demo): sum of paid orders per store
            m.put("currency", s.getCurrency() != null ? s.getCurrency() : "BDT");
            m.put("ownerEmail", "");
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/plans")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> getPlans() {
        // Platform pricing plans — static defaults for now (Lokzon-matched: 399/999/1999).
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("starter", Map.of("monthly", 399, "yearly", 3990));
        m.put("growth", Map.of("monthly", 999, "yearly", 9990));
        m.put("pro", Map.of("monthly", 1999, "yearly", 19990));
        return m;
    }

    @PatchMapping("/stores/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> updateStore(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // Platform-admin store management: the merchant PATCH /stores/{id} requires
        // OWNER/ADMIN + tenant scope, so it 403s for platform admins.
        Store s = stores.findById(StoreScope.lid(id)).orElseThrow(() -> ApiException.notFound("Store"));
        if (body.get("plan") instanceof String p && List.of("starter", "growth", "pro").contains(p.toLowerCase()))
            s.setPlanName(p.toLowerCase());
        if (body.get("status") instanceof String st) {
            if ("Suspended".equalsIgnoreCase(st)) s.setStatus("suspended");
            else if ("Active".equalsIgnoreCase(st)) s.setStatus("active");
        }
        stores.save(s);
        return Map.of("id", id, "ok", true);
    }

    @DeleteMapping("/stores/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> deleteStore(@PathVariable String id) {
        Store s = stores.findById(StoreScope.lid(id)).orElseThrow(() -> ApiException.notFound("Store"));
        s.setStatus("suspended"); // soft delete, same as the merchant flow
        stores.save(s);
        return Map.of("id", id, "ok", true);
    }

    @PutMapping("/plans")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> savePlans(@RequestBody Map<String, Object> body) {
        // Persist to DB in production; echo back for now.
        return body;
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<Map<String, Object>> listTickets() {
        // Support tickets — no backend table yet; return empty.
        return List.of();
    }

    @PostMapping("/tickets/{id}/reply")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> replyTicket(@PathVariable String id,
                                           @RequestBody Map<String, Object> body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("reply", body.get("text"));
        return m;
    }

    @PatchMapping("/tickets/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> setTicketStatus(@PathVariable String id,
                                               @RequestBody Map<String, Object> body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("status", body.get("status"));
        return m;
    }

    @GetMapping("/announcements")
    public List<Map<String, Object>> listAnnouncements() {
        // Published platform announcements — visible to all store owners.
        return List.of();
    }

    @PostMapping("/announcements")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> createAnnouncement(@RequestBody Map<String, Object> body) {
        Map<String, Object> m = new LinkedHashMap<>(body);
        m.put("id", java.util.UUID.randomUUID().toString());
        return m;
    }

    @PatchMapping("/announcements/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> updateAnnouncement(@PathVariable String id,
                                                  @RequestBody Map<String, Object> body) {
        Map<String, Object> m = new LinkedHashMap<>(body);
        m.put("id", id);
        return m;
    }

    @DeleteMapping("/announcements/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void removeAnnouncement(@PathVariable String id) {
    }

    @PostMapping("/stores/{storeId}/impersonate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> impersonate(@PathVariable String storeId) {
        // Audit-logged impersonation — returns a User for the store.
        // Full implementation needs audit logging; return stub for now.
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "impersonated");
        m.put("storeId", storeId);
        m.put("impersonated", true);
        return m;
    }
}
