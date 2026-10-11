package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates that the storeId in the URL matches the authenticated user's tenant.
 * The frontend uses /stores/{storeId}/... URL patterns; the backend enforces
 * tenant isolation by comparing the URL storeId against the JWT's store_id claim.
 */
@Component
public class StoreScope {

  public Long require(String storeId) {
    Long urlStoreId;
    try {
      urlStoreId = Long.valueOf(storeId);
    } catch (NumberFormatException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_ID", "Invalid store id: " + storeId);
    }
    Long tokenStoreId = TenantContext.storeId();
    if (tokenStoreId == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
    if (!tokenStoreId.equals(urlStoreId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH",
          "You do not have access to this store");
    }
    return urlStoreId;
  }

  public static String sid(Long id) {
    return id == null ? null : id.toString();
  }

  public static Long lid(String id) {
    if (id == null || id.isEmpty()) return null;
    try {
      return Long.valueOf(id);
    } catch (NumberFormatException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "Invalid id: " + id);
    }
  }

  /**
   * Returns true if the ID represents a new (not yet persisted) entity.
   * Frontend sends crypto.randomUUID() for new entities via PUT.
   */
  public static boolean isNewId(String id) {
    if (id == null || id.isEmpty()) return true;
    // UUID format: 8-4-4-4-12 hex digits
    return id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  }
}
