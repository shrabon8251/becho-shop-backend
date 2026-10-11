package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.customers.entity.Customer;
import com.becho.saas.modules.customers.repository.CustomerRepository;
import com.becho.saas.modules.orders.repository.OrderRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.security.GoogleIdTokenVerifier;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible shopper account endpoints: /stores/{storeId}/account[/{id}...].
 * Signup/login/google are public (no merchant JWT); the store comes from the URL.
 */
@RestController
@RequestMapping("/stores/{storeId}/account")
public class StoreAccountController {

  /** Dummy bcrypt hash for timing-safe login when the email doesn't exist. */
  private static final String DUMMY_HASH =
      "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEW";

  private final CustomerRepository customers;
  private final OrderRepository orders;
  private final StoreRepository stores;
  private final PasswordEncoder passwordEncoder;
  private final GoogleIdTokenVerifier googleVerifier;
  private final StoreScope scope;

  public StoreAccountController(CustomerRepository customers,
                                 OrderRepository orders, StoreRepository stores,
                                 PasswordEncoder passwordEncoder,
                                 GoogleIdTokenVerifier googleVerifier, StoreScope scope) {
    this.customers = customers;
    this.orders = orders;
    this.stores = stores;
    this.passwordEncoder = passwordEncoder;
    this.googleVerifier = googleVerifier;
    this.scope = scope;
  }

  // ---------- Public: signup / login / google ----------

  @PostMapping("/signup")
  @Transactional
  public Map<String, Object> signup(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
      Long sid = StoreScope.lid(storeId);
      Object emailObj = body.get("email");
      if (!(emailObj instanceof String emailStr) || !emailStr.contains("@")) {
          throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_EMAIL", "Valid email is required");
      }
      String email = emailStr.toLowerCase().trim();
      String password = (String) body.get("password");
      if (password == null || password.length() < 8) {
          throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                  "Password must be at least 8 characters");
      }
      if (customers.findByStoreIdAndEmailAndDeletedAtIsNull(sid, email).isPresent()) {
          // Enumeration-safe: return 200 with exists flag instead of 409.
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("exists", true);
          m.put("message", "This email is already registered. Please log in.");
          return m;
      }

      Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
    Customer c = new Customer();
    c.setStore(store);
    c.setEmail(email);
    c.setPhone((String) body.get("phone"));
    c.setFirstName((String) body.get("name"));
    c.setPasswordHash(passwordEncoder.encode(password));
    customers.save(c);
    return toFrontend(c);
  }

  @PostMapping("/login")
  @Transactional
  public Map<String, Object> login(@PathVariable String storeId,
                                    @RequestBody Map<String, Object> body) {
    Long sid = StoreScope.lid(storeId);
      Object emailObj = body.get("email");
      if (!(emailObj instanceof String emailStr) || !emailStr.contains("@")) {
          throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_EMAIL", "Valid email is required");
      }
      String email = emailStr.toLowerCase().trim();

      String password = (String) body.get("password");
    var found = customers.findByStoreIdAndEmailAndDeletedAtIsNull(sid, email);
    if (found.isEmpty()) {
      // Timing-safe: run a dummy bcrypt check so unknown emails don't respond faster.
      passwordEncoder.matches(password, DUMMY_HASH);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
          "Invalid email or password");
    }
    Customer c = found.get();
    if (c.isLockedOut()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
          "Too many failed login attempts. Try again later.");
    }
    if (c.getPasswordHash() == null || !passwordEncoder.matches(password, c.getPasswordHash())) {
      // Count the failure; lock the account after 5 consecutive failures.
      int attempts = c.getFailedLoginAttempts() + 1;
      if (attempts >= 5) {
        c.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
        c.setFailedLoginAttempts(0);
      } else {
        c.setFailedLoginAttempts(attempts);
      }
      customers.save(c);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password");
    }
    // Success: clear any failure state.
    c.setFailedLoginAttempts(0);
    c.setLockedUntil(null);
    customers.save(c);
    return toFrontend(c);
  }

  /**
   * Shopper Google sign-in. Requires the real Google-issued ID token, verified
   * server-side (signature, issuer, audience, expiry). A bare {name, email}
   * is rejected — otherwise anyone could take over any shopper account.
   */
  @PostMapping("/google")
  @Transactional
  public Map<String, Object> google(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
    Long sid = StoreScope.lid(storeId);
    String idToken = (String) body.get("idToken");
    GoogleIdTokenVerifier.GoogleUser gu = googleVerifier.verify(idToken);
    String email = gu.email();
    Customer c = customers.findByStoreIdAndEmailAndDeletedAtIsNull(sid, email).orElse(null);
    if (c == null) {
      Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));
      c = new Customer();
      c.setStore(store);
      c.setEmail(email);
      c.setFirstName(gu.name());
      c.setEmailVerifiedAt(LocalDateTime.now());
      customers.save(c);
    }
    return toFrontend(c);
  }

  // ---------- Authenticated (merchant JWT, store-scoped) ----------

  @PostMapping("/{id}/verify-email")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  @Transactional
  public Map<String, Object> verifyEmail(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Customer c = find(sid, id);
    c.setEmailVerifiedAt(LocalDateTime.now());
    customers.save(c);
    return toFrontend(c);
  }

  @PostMapping("/{id}/verify-phone")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  @Transactional
  public Map<String, Object> verifyPhone(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Customer c = find(sid, id);
    c.setPhoneVerifiedAt(LocalDateTime.now());
    customers.save(c);
    return toFrontend(c);
  }

  @GetMapping("/{id}/orders")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  @Transactional(readOnly = true)
  public List<Map<String, Object>> orders(@PathVariable String storeId, @PathVariable String id) {
    Long sid = scope.require(storeId);
    Customer c = find(sid, id);
    String email = c.getEmail();
    return orders.findPage(sid, null, null, null, Limit.of(100)).stream()
        .filter(o -> (o.getCustomer() != null && o.getCustomer().getId().equals(c.getId()))
            || (email != null && email.equalsIgnoreCase(o.getEmail())))
        .map(o -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", o.getId());
          m.put("code", "#" + o.getOrderNumber());
          m.put("total", o.getTotalMinor());
          m.put("currency", o.getCurrency());
          m.put("pay", o.getFinancialStatus());
          m.put("ful", o.getFulfillmentStatus());
          m.put("createdAt", o.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
          return m;
        })
        .collect(Collectors.toList());
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  @Transactional
  public Map<String, Object> update(@PathVariable String storeId,
                                     @PathVariable String id,
                                     @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Customer c = find(sid, id);
    if (body.get("name") != null) c.setFirstName((String) body.get("name"));
    if (body.get("phone") != null) c.setPhone((String) body.get("phone"));
    if (body.get("email") != null) c.setEmail(((String) body.get("email")).toLowerCase().trim());
    customers.save(c);
    return toFrontend(c);
  }

  @PutMapping("/{id}/password")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
  @Transactional
  public void changePassword(@PathVariable String storeId,
                              @PathVariable String id,
                              @RequestBody Map<String, Object> body) {
    Long sid = scope.require(storeId);
    Customer c = find(sid, id);
    String password = (String) body.get("password");
    if (password == null || password.length() < 8) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
          "Password must be at least 8 characters");
    }
    c.setPasswordHash(passwordEncoder.encode(password));
    customers.save(c);
  }

  private Customer find(Long sid, String id) {
    return customers.findByIdAndStoreIdAndDeletedAtIsNull(StoreScope.lid(id), sid)
        .orElseThrow(() -> ApiException.notFound("Customer"));
  }

  private Map<String, Object> toFrontend(Customer c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId().toString());
    String name = ((c.getFirstName() != null ? c.getFirstName() : "") + " " +
                   (c.getLastName() != null ? c.getLastName() : "")).trim();
    m.put("name", name.isEmpty() ? c.getEmail() : name);
    m.put("email", c.getEmail());
    m.put("phone", c.getPhone());
    m.put("addresses", List.of());
    m.put("wishlist", List.of());
    m.put("tags", List.of());
    m.put("emailVerified", c.isEmailVerified());
    m.put("phoneVerified", c.isPhoneVerified());
    return m;
  }
}
