package com.becho.saas.modules.customers.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.KeysetPage;
import com.becho.saas.modules.customers.entity.Customer;
import com.becho.saas.modules.customers.repository.CustomerRepository;
import com.becho.saas.modules.platform.service.AutomationService;
import com.becho.saas.modules.customers.web.CustomerDtos.*;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final StoreRepository stores;

    private final AutomationService automations;

    public CustomerService(CustomerRepository customers, StoreRepository stores,
                           AutomationService automations) {
        this.customers = customers;
        this.stores = stores;
        this.automations = automations;
    }

    @Transactional(readOnly = true)
    public KeysetPage<CustomerDto> list(String cursor, Integer size) {
        Long storeId = TenantContext.requireStoreId();
        int pageSize = Math.min(size == null ? KeysetPage.DEFAULT_SIZE : size, KeysetPage.MAX_SIZE);
        var decoded = KeysetPage.decode(cursor);
        List<Customer> rows = customers.findPage(storeId,
                decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.id(),
                Limit.of(pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        List<Customer> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            var last = page.get(page.size() - 1);
            nextCursor = KeysetPage.encode(last.getCreatedAt(), last.getId());
        }
        return new KeysetPage<>(page.stream().map(this::toDto).toList(), nextCursor, hasMore);
    }

    @Transactional
    public CustomerDto create(CreateCustomerRequest req) {
        Long storeId = TenantContext.requireStoreId();
        if (req.email() != null && customers.findByStoreIdAndEmailAndDeletedAtIsNull(storeId, req.email()).isPresent()) {
            throw ApiException.conflict("EMAIL_TAKEN", "Customer email already exists");
        }
        Store store = stores.findById(storeId).orElseThrow(() -> ApiException.notFound("Store"));
        Customer c = new Customer();
        c.setStore(store);
        c.setEmail(req.email());
        c.setPhone(req.phone());
        c.setFirstName(req.firstName());
        c.setLastName(req.lastName());
        c.setAcceptsMarketing(Boolean.TRUE.equals(req.acceptsMarketing()));
        c.setNote(req.note());
        Customer saved = customers.save(c);
        // Fire welcome automation (best-effort, after commit via fire()).
        try {
            String name = ((saved.getFirstName() != null ? saved.getFirstName() : "") + " " +
                    (saved.getLastName() != null ? saved.getLastName() : "")).trim();
            automations.fire(storeId, "welcome", saved.getEmail(), name, Map.of());
        } catch (Exception e) { /* best-effort */ }
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public CustomerDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public CustomerDto update(Long id, Map<String, Object> patch) {
        Customer c = find(id);
        if (patch.get("firstName") instanceof String s) c.setFirstName(s.trim());
        if (patch.get("lastName") instanceof String s) c.setLastName(s.trim());
        if (patch.get("phone") instanceof String s) c.setPhone(s.trim());
        if (patch.get("note") instanceof String s) c.setNote(s);
        if (patch.get("credit") instanceof Number n) c.setCreditMinor(n.longValue());
        if (patch.get("tags") instanceof String s) c.setTags(s);
        return toDto(customers.save(c));
    }

    private Customer find(Long id) {
        return customers.findByIdAndStoreIdAndDeletedAtIsNull(id, TenantContext.requireStoreId())
                .orElseThrow(() -> ApiException.notFound("Customer"));
    }

    @Transactional
    public void delete(Long id) {
        Customer c = find(id);
        c.setDeletedAt(java.time.LocalDateTime.now());
        customers.save(c);
    }

    private CustomerDto toDto(Customer c) {
        return new CustomerDto(c.getId(), c.getEmail(), c.getPhone(), c.getFirstName(), c.getLastName(),
                c.getTotalSpentMinor(), c.getOrdersCount(), c.getStatus(), c.getCreatedAt(), c.getCreditMinor());
    }
}
