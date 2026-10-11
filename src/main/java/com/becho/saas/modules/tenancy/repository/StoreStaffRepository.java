package com.becho.saas.modules.tenancy.repository;

import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.entity.StoreStaff;
import com.becho.saas.modules.tenancy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StoreStaffRepository extends JpaRepository<StoreStaff, Long> {
  Optional<StoreStaff> findByStoreAndUserAndStatus(Store store, User user, String status);
  List<StoreStaff> findByUserAndStatus(User user, String status);
  List<StoreStaff> findByStoreIdAndStatus(Long storeId, String status);
  Optional<StoreStaff> findByIdAndStoreId(Long id, Long storeId);
}
