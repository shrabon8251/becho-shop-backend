package com.becho.saas.modules.payments.providers;

import com.becho.saas.modules.payments.PaymentProvider;
import org.springframework.stereotype.Component;

/**
 * Manual mobile-banking (bKash/Nagad/Rocket TrxID): customer pays to the
 * merchant's number and submits the transaction ID. autoConfirms() = false —
 * a staff member must verify the TrxID and confirm, per the standing decision.
 */
@Component
public class ManualMfsProvider implements PaymentProvider {

  @Override
  public String key() { return "manual_mfs"; }

  @Override
  public boolean autoConfirms() { return false; }

  @Override
  public InitResult initPayment(InitRequest req) {
    String instructions = "Send " + (req.amountMinor() / 100.0) + " " + req.currency()
        + " to the merchant number shown at checkout, then submit your TrxID. "
        + "Your order will be confirmed after manual verification.";
    return new InitResult(null, "MANUAL-" + req.orderId(), instructions);
  }

  @Override
  public VerifyResult verifyCallback(CallbackPayload payload) {
    // No automatic verification — staff confirms in the admin panel.
    String trxId = payload.params().getOrDefault("trx_id", "");
    return VerifyResult.of(false, trxId, 0, "pending_manual", "{}");
  }
}
