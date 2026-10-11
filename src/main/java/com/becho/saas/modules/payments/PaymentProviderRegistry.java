package com.becho.saas.modules.payments;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Registry — providers are discovered, never hard-coded at call sites. */
@Component
public class PaymentProviderRegistry {

  private final Map<String, PaymentProvider> providers;

  public PaymentProviderRegistry(List<PaymentProvider> providers) {
    this.providers = providers.stream()
        .collect(Collectors.toMap(PaymentProvider::key, Function.identity()));
  }

  public PaymentProvider get(String key) {
    PaymentProvider p = providers.get(key);
    if (p == null) throw new IllegalArgumentException("Unknown payment provider: " + key);
    return p;
  }
}
