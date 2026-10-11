package com.becho.saas.modules.payments.providers;

import com.becho.saas.modules.payments.PaymentProvider;
import org.springframework.stereotype.Component;

/** Cash on Delivery — no online settlement; always manual. */
@Component
public class CodProvider implements PaymentProvider {

  @Override
  public String key() { return "cod"; }

  @Override
  public boolean autoConfirms() { return false; }

  @Override
  public InitResult initPayment(InitRequest req) {
    return new InitResult(null, "COD-" + req.orderId(),
        "Pay " + (req.amountMinor() / 100.0) + " " + req.currency() + " in cash on delivery.");
  }

  @Override
  public VerifyResult verifyCallback(CallbackPayload payload) {
    return VerifyResult.of(false, null, 0, "pending_manual", "{}");
  }
}
