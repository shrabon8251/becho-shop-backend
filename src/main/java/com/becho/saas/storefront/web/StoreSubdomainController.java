package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public store lookup by subdomain slug.
 * Enables shareable URLs like /shop/fitcheck instead of /shop?store=90006.
 */
@RestController
@RequestMapping("/api/storefront/by-subdomain")
public class StoreSubdomainController {

    private final StoreRepository stores;

    public StoreSubdomainController(StoreRepository stores) {
        this.stores = stores;
    }

    @GetMapping("/{subdomain}")
    public Map<String, Object> bySubdomain(@PathVariable String subdomain) {
        Store s = stores.findBySubdomain(subdomain.toLowerCase().trim())
                .orElseThrow(() -> ApiException.notFound("Store"));
        return Map.of("storeId", s.getId().toString(), "name", s.getName(), "subdomain", s.getSubdomain());
    }
}
