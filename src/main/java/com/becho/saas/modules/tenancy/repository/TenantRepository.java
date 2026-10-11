package com.becho.saas.modules.tenancy.repository;

import com.becho.saas.modules.tenancy.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
