package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.customers.service.CustomerService;
import com.becho.saas.modules.customers.web.CustomerDtos.CreateCustomerRequest;
import com.becho.saas.modules.customers.web.CustomerDtos.CustomerDto;
import com.becho.saas.modules.wallet.service.WalletService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible customer endpoints: /stores/{storeId}/customers[/{id}].
 */
@RestController
@RequestMapping("/stores/{storeId}/customers")
public class StoreCustomerController {

    private final CustomerService customerService;
    private final WalletService walletService;
    private final StoreScope scope;

    public StoreCustomerController(CustomerService customerService, WalletService walletService, StoreScope scope) {
        this.customerService = customerService;
        this.walletService = walletService;
        this.scope = scope;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public List<Map<String, Object>> list(@PathVariable String storeId) {
        scope.require(storeId);
        var page = customerService.list(null, 100);
        return page.items().stream().map(this::toFrontend).collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> create(@PathVariable String storeId,
                                      @RequestBody Map<String, Object> body) {
        scope.require(storeId);
        CustomerDto dto = customerService.create(new CreateCustomerRequest(
                (String) body.get("email"),
                (String) body.get("phone"),
                (String) body.get("name"),
                null, null,
                (String) body.get("notes")));
        return toFrontend(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> save(@PathVariable String storeId,
                                    @PathVariable String id,
                                    @RequestBody Map<String, Object> body) {
        scope.require(storeId);
        if (StoreScope.isNewId(id)) {
            return create(storeId, body);
        }
        // Actually persist the update (was a no-op stub returning stale data).
        CustomerDto dto = customerService.update(StoreScope.lid(id), body);
        return toFrontend(dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void remove(@PathVariable String storeId, @PathVariable String id) {
        scope.require(storeId);
        customerService.delete(StoreScope.lid(id));
    }

    @PostMapping("/{id}/points")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> adjustPoints(@PathVariable String storeId,
                                            @PathVariable String id,
                                            @RequestBody Map<String, Object> body) {
        scope.require(storeId);
        Long customerId = StoreScope.lid(id);
        int delta = body.get("delta") instanceof Number n ? n.intValue() : 0;
        String reason = body.get("reason") instanceof String s ? s : "Manual adjustment";
        // Actually adjust the points via WalletService (was a no-op stub before).
        if (delta > 0) {
            walletService.earnPoints(customerId, delta);
        } else if (delta < 0) {
            walletService.redeemPoints(customerId, -delta);
        }
        CustomerDto dto = customerService.get(customerId);
        Map<String, Object> m = toFrontend(dto);
        m.put("pointsDelta", delta);
        m.put("reason", reason);
        return m;
    }

    private Map<String, Object> toFrontend(CustomerDto c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id().toString());
        String name = ((c.firstName() != null ? c.firstName() : "") + " " +
                (c.lastName() != null ? c.lastName() : "")).trim();
        m.put("name", name.isEmpty() ? c.email() : name);
        m.put("email", c.email());
        m.put("phone", c.phone());
        m.put("notes", "");
        m.put("addresses", List.of());
        m.put("tags", List.of());
        m.put("totalSpent", c.totalSpentMinor());
        m.put("ordersCount", c.ordersCount());
        m.put("credit", c.creditMinor());
        return m;
    }
}
