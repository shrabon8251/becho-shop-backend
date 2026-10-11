package com.becho.saas.modules.payments;

/**
 * Strategy interface for payment providers (Open/Closed Principle):
 * new gateways plug in without touching order code.
 */
public interface PaymentProvider {

  /** Gateway key: sslcommerz | bkash | nagad | rocket | stripe | manual_mfs | cod */
  String key();

  /** Create a payment session; returns redirect/checkout URL or instructions. */
  InitResult initPayment(InitRequest req);

  /** Verify an IPN/callback payload. Must throw on invalid signature. */
  VerifyResult verifyCallback(CallbackPayload payload);

  /** True for providers that settle automatically (IPN) vs manual confirmation. */
  default boolean autoConfirms() { return true; }

  record InitRequest(Long storeId, Long orderId, long amountMinor, String currency,
                     String customerEmail, String customerPhone, String successUrl,
                     String failUrl, String cancelUrl) {}

  record InitResult(String checkoutUrl, String gatewaySessionId, String instructions) {}

  record CallbackPayload(java.util.Map<String, String> params, String rawBody) {}

  /**
   * Result of server-to-server callback verification.
   * merchantTranId/currency are echoed from the gateway so the controller can
   * cross-check them against the order — never trust amount alone.
   */
  record VerifyResult(boolean success, String gatewayTxnId, long amountMinor,
                      String currency, String merchantTranId, String status, String rawJson) {

    /** Backwards-compatible factory when the gateway doesn't echo currency/tran id. */
    public static VerifyResult of(boolean success, String gatewayTxnId, long amountMinor,
                                  String status, String rawJson) {
      return new VerifyResult(success, gatewayTxnId, amountMinor, null, null, status, rawJson);
    }
  }
}
