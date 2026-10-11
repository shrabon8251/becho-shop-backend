package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.tenancy.entity.StoreStaff;
import com.becho.saas.modules.tenancy.entity.User;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.modules.tenancy.repository.StoreStaffRepository;
import com.becho.saas.modules.tenancy.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible staff endpoints: /stores/{storeId}/staff[/{id}].
 * Frontend Crud&lt;StaffMember&gt;: { id, name, email, role: "Admin"|"Manager"|"Support", permissions? }.
 * Creating a member also creates/invites their login (a User row) server-side.
 */
@RestController
@RequestMapping("/stores/{storeId}/staff")
public class StoreStaffController {

    private final StoreStaffRepository staff;
    private final StoreRepository stores;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final StoreScope scope;

    public StoreStaffController(StoreStaffRepository staff, StoreRepository stores,
                                UserRepository users, PasswordEncoder passwordEncoder,
                                StoreScope scope) {
        this.staff = staff;
        this.stores = stores;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.scope = scope;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public List<Map<String, Object>> list(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        return staff.findByStoreIdAndStatus(sid, "active")
                .stream().map(this::toFrontend).collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> create(@PathVariable String storeId,
                                      @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        String email = ((String) body.get("email")).toLowerCase().trim();
        String name = (String) body.getOrDefault("name", email);

        // Find-or-create the login user (invite flow; production sends an invite email).
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setFullName(name);
            // The invite UI tells the new staff their password is "welcome123",
            // so the account must actually use it (a random password = login impossible).
            // Staff should change it after first login (Profile settings).
            user.setPasswordHash(passwordEncoder.encode("welcome123"));
            user.setStatus("active");
            users.save(user);
        }

        var store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
        // Avoid duplicate memberships.
        var existing = staff.findByStoreAndUserAndStatus(store, user, "active");
        StoreStaff member = existing.orElseGet(StoreStaff::new);
        String role = toBackendRole((String) body.get("role"));
        // Only an OWNER may grant the OWNER role — otherwise an ADMIN could
        // promote themselves or anyone else to owner (privilege escalation).
        if ("OWNER".equals(role) && !callerIsOwner()) {
            throw ApiException.forbidden();
        }
        member.setStore(store);
        member.setUser(user);
        member.setRole(role);
        member.setStatus("active");
        Object perms = body.get("permissions");
        if (perms instanceof List<?> l) {
            member.setPermissions(String.join(",", l.stream().map(Object::toString).toList()));
        }
        return toFrontend(staff.save(member));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> save(@PathVariable String storeId,
                                    @PathVariable String id,
                                    @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        if (StoreScope.isNewId(id)) return create(storeId, body);
        StoreStaff member = staff.findByIdAndStoreId(StoreScope.lid(id), sid)
                .orElseThrow(() -> ApiException.notFound("Staff member"));
        boolean wasOwner = "OWNER".equalsIgnoreCase(member.getRole());
        String newRole = body.get("role") != null ? toBackendRole((String) body.get("role")) : null;
        // Granting OWNER, or demoting an existing OWNER, requires the caller to be
        // an OWNER — otherwise an ADMIN could seize ownership or lock owners out.
        if ((newRole != null && ("OWNER".equals(newRole) != wasOwner)) && !callerIsOwner()) {
            throw ApiException.forbidden();
        }
        // Never demote the last owner — the store would be left ownerless.
        if (wasOwner && newRole != null && !"OWNER".equals(newRole)) {
            long owners = staff.findByStoreIdAndStatus(sid, "active").stream()
                    .filter(m -> "owner".equalsIgnoreCase(m.getRole())).count();
            if (owners <= 1) {
                throw ApiException.badRequest("LAST_OWNER", "Cannot demote the last owner of the store");
            }
        }
        if (newRole != null) member.setRole(newRole);
        Object perms = body.get("permissions");
        if (perms instanceof List<?> l) {
            member.setPermissions(String.join(",", l.stream().map(Object::toString).toList()));
        }
        if (body.get("name") != null && member.getUser() != null) {
            member.getUser().setFullName((String) body.get("name"));
            users.save(member.getUser());
        }
        return toFrontend(staff.save(member));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void remove(@PathVariable String storeId, @PathVariable String id) {
        Long sid = scope.require(storeId);
        StoreStaff member = staff.findByIdAndStoreId(StoreScope.lid(id), sid)
                .orElseThrow(() -> ApiException.notFound("Staff member"));
        // Never remove the last owner; only an OWNER may remove a co-owner.
        long owners = staff.findByStoreIdAndStatus(sid, "active").stream()
                .filter(m -> "owner".equalsIgnoreCase(m.getRole())).count();
        if ("owner".equalsIgnoreCase(member.getRole())) {
            if (owners <= 1) {
                throw ApiException.badRequest("LAST_OWNER", "Cannot remove the last owner of the store");
            }
            if (!callerIsOwner()) {
                throw ApiException.forbidden();
            }
        }
        member.setStatus("inactive");
        staff.save(member);
    }

    /** The caller's role for the scoped store, from the JWT authorities. */
    private boolean callerIsOwner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));
    }

    private String toBackendRole(String frontendRole) {
        if (frontendRole == null) return "STAFF";
        return switch (frontendRole) {
            case "Admin" -> "ADMIN";
            case "Support" -> "SUPPORT";
            case "Manager" -> "STAFF";
            default -> frontendRole.toUpperCase();
        };
    }

    private String toFrontendRole(String backendRole) {
        if (backendRole == null) return "Manager";
        return switch (backendRole.toUpperCase()) {
            case "OWNER", "ADMIN" -> "Admin";
            case "SUPPORT" -> "Support";
            default -> "Manager";
        };
    }

    private Map<String, Object> toFrontend(StoreStaff m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId().toString());
        User u = m.getUser();
        out.put("name", u != null ? u.getFullName() : "");
        out.put("email", u != null ? u.getEmail() : "");
        out.put("role", toFrontendRole(m.getRole()));
        if (m.getPermissions() != null && !m.getPermissions().isBlank()) {
            out.put("permissions", List.of(m.getPermissions().split(",")));
        }
        out.put("status", m.getStatus());
        return out;
    }
}
