package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.entity.Tenant;
import com.becho.saas.modules.tenancy.entity.User;
import com.becho.saas.modules.tenancy.entity.StoreStaff;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.modules.tenancy.repository.TenantRepository;
import com.becho.saas.modules.tenancy.repository.UserRepository;
import com.becho.saas.modules.tenancy.repository.StoreStaffRepository;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frontend-compatible store endpoints: GET /stores/{id}, GET /stores/available,
 * POST /stores, PATCH /stores/{id}, DELETE /stores/{id}, billing endpoints.
 */
@RestController
@RequestMapping("/stores")
public class StoreController {

    private final StoreRepository stores;
    private final StoreScope scope;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final StoreStaffRepository staff;

    public StoreController(StoreRepository stores, StoreScope scope,
                           TenantRepository tenants, UserRepository users,
                           StoreStaffRepository staff) {
        this.stores = stores;
        this.scope = scope;
        this.tenants = tenants;
        this.users = users;
        this.staff = staff;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public Map<String, Object> get(@PathVariable String id) {
        scope.require(id);
        Store s = stores.findById(StoreScope.lid(id)).orElseThrow(() -> ApiException.notFound("Store"));
        return toFrontend(s);
    }

    @GetMapping("/available")
    public Map<String, Object> available(@RequestParam String sub) {
        boolean exists = stores.findBySubdomain(sub).isPresent();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", !exists);
        return m;
    }

    /**
     * Creates a store for the authenticated user (onboarding flow).
     * Any signed-in user may create a store and becomes its OWNER — this is how
     * first-time merchants (email or Google signups) get their store after the
     * no-store onboarding. Trial: 14 days from creation.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Long userId = TenantContext.userId();
        if (userId == null) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "NO_USER", "No authenticated user");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        String name = body.get("name") == null ? "My Store" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) name = "My Store";

        Store store = new Store();
        store.setName(name);
        store.setSlug(slugify(name));
        // Honor the caller's chosen subdomain when it is valid and free (the
        // frontend checks /stores/available first); otherwise derive from the name.
        // Ensure subdomain unique either way.
        String requested = body.get("sub") == null ? "" : slugify(String.valueOf(body.get("sub")));
        String baseSub = (!requested.isEmpty() && requested.length() >= 3
                && stores.findBySubdomain(requested).isEmpty()) ? requested : slugify(name);
        if (baseSub.isEmpty()) baseSub = "store";
        String sub = baseSub;
        int attempt = 0;
        while (stores.findBySubdomain(sub).isPresent()) {
            attempt++;
            sub = baseSub + "-" + (attempt == 1 ? user.getId() : user.getId() + "-" + attempt);
            if (attempt > 10) {
                sub = baseSub + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }
        store.setSubdomain(sub);
        // Tenant requires a non-null name and a unique slug — reuse the store
        // name and the (already unique-checked) subdomain.
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setSlug(sub);
        tenants.save(tenant);
        store.setTenant(tenant);
        Object plan = body.get("plan");
        if (plan != null && !String.valueOf(plan).isBlank()) store.setPlanName(String.valueOf(plan).trim());
        Object currency = body.get("currency");
        if (currency != null && !String.valueOf(currency).isBlank()) store.setCurrency(String.valueOf(currency).trim().toUpperCase());
        // Trial: 14 days from store creation (persisted; not derived from createdAt).
        store.setTrialEndsAt(java.time.LocalDateTime.now().plusDays(14));
        // The entity defaults status to "active"; a fresh store is on trial, so the
        // Billing page can show the trial countdown (gated on status "Trial").
        store.setStatus("trial");
        stores.save(store);

        StoreStaff owner = new StoreStaff();
        owner.setStore(store);
        owner.setUser(user);
        owner.setRole("owner");
        owner.setStatus("active");
        staff.save(owner);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", store.getId().toString());
        m.put("name", store.getName());
        m.put("subdomain", store.getSubdomain());
        return m;
    }

    private static String slugify(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> update(@PathVariable String id,
                                      @RequestBody Map<String, Object> body) {
        scope.require(id);
        Store s = stores.findById(StoreScope.lid(id)).orElseThrow(() -> ApiException.notFound("Store"));
        if (body.get("name") != null) s.setName((String) body.get("name"));
        if (body.get("currency") != null) s.setCurrency((String) body.get("currency"));
        stores.save(s);
        return toFrontend(s);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER')")
    public void remove(@PathVariable String id) {
        scope.require(id);
        // Soft delete — mark as suspended.
        Store s = stores.findById(StoreScope.lid(id)).orElseThrow(() -> ApiException.notFound("Store"));
        s.setStatus("suspended");
        stores.save(s);
    }

    @PutMapping("/{id}/billing/card")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> setCard(@PathVariable String id,
                                       @RequestBody Map<String, Object> body) {
        scope.require(id);
        Store s = stores.findById(StoreScope.lid(id)).orElseThrow(() -> ApiException.notFound("Store"));
        // In production, use a payment provider token. Store last4 for display.
        return toFrontend(s);
    }

    // NOTE: POST /{id}/billing/subscribe now lives in StoreBillingController
    // with real server-side pricing + promo validation. Kept here only the
    // card endpoint (stores last4 for display; real charging uses a provider).

    private Map<String, Object> toFrontend(Store s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("name", s.getName());
        m.put("sub", s.getSubdomain());
        m.put("currency", s.getCurrency() != null ? s.getCurrency() : "BDT");
        // Frontend Store shape: {id, name, sub, plan, status, currency, createdAt, gmv, invoices, ...}.
        // plan/invoices were missing -> Billing page crashed on store.invoices.map().
        String plan = s.getPlanName();
        if (plan == null || plan.isBlank()) plan = "Starter";
        m.put("plan", plan);
        m.put("status", toStoreStatus(s));
        m.put("billingStatus", s.getBillingStatus());
        m.put("planExpiresAt", null);
        m.put("invoices", List.of());
        m.put("gmv", 0);
        m.put("trialEndsAt", s.getTrialEndsAt() != null ? s.getTrialEndsAt().toString() : null);
        if (s.getBillingMethodKind() != null) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("kind", s.getBillingMethodKind());
            pm.put("label", s.getBillingMethodLabel());
            pm.put("trxId", s.getBillingMethodTrxid());
            m.put("payMethod", pm);
        }
        return m;
    }

    private String toStoreStatus(Store s) {
        String st = s.getStatus();
        if (st == null || st.isBlank() || "active".equalsIgnoreCase(st.trim())) {
            // Stores created before the trial-status fix keep the entity default
            // "active" — but a future trialEndsAt means the store is still on its
            // 14-day trial, so report "Trial" (fixes the countdown for existing
            // stores with no data migration). Paid stores have trialEndsAt in the
            // past and keep reporting "Active".
            java.time.LocalDateTime trialEndsAt = s.getTrialEndsAt();
            if (trialEndsAt != null && java.time.LocalDateTime.now().isBefore(trialEndsAt)) return "Trial";
            return "Active";
        }
        return switch (st.trim().toLowerCase()) {
            case "trial" -> "Trial";
            case "suspended" -> "Suspended";
            default -> "Active";
        };
    }
}
