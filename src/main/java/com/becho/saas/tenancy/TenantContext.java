package com.becho.saas.tenancy;

/**
 * Holds the current request's tenant scope. Set by TenantFilter from the
 * authenticated principal (or storefront domain), cleared after the request.
 * Services MUST read storeId from here — never from client-supplied params.
 */
public final class TenantContext {

  private static final ThreadLocal<Long> STORE_ID = new ThreadLocal<>();
  private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(Long storeId, Long userId) {
    STORE_ID.set(storeId);
    USER_ID.set(userId);
  }

  public static Long storeId() {
    return STORE_ID.get();
  }

  /** Returns storeId or throws — store-scoped endpoints must not run unscoped. */
  public static Long requireStoreId() {
    Long id = STORE_ID.get();
    if (id == null) {
      throw new IllegalStateException("No tenant scope for this request");
    }
    return id;
  }

  public static Long userId() {
    return USER_ID.get();
  }

  public static void clear() {
    STORE_ID.remove();
    USER_ID.remove();
  }
}
