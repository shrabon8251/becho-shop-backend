package com.becho.saas.modules.shipping.service;

import com.becho.saas.modules.shipping.entity.*;
import com.becho.saas.modules.shipping.repository.*;
import com.becho.saas.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Shipping + tax quotes. Server-side math only; rates come from store config.
 */
@Service
public class QuoteService {

  private final ShippingZoneRepository zones;
  private final TaxRateRepository taxRates;

  public QuoteService(ShippingZoneRepository zones, TaxRateRepository taxRates) {
    this.zones = zones;
    this.taxRates = taxRates;
  }

  public record ShippingQuote(Long rateId, String name, long priceMinor, Integer minDays, Integer maxDays) {}
  public record TaxQuote(String name, long taxMinor) {}

  @Transactional(readOnly = true)
  public List<ShippingQuote> shippingQuotes(String country, long subtotalMinor, int totalWeightGrams) {
    Long storeId = TenantContext.requireStoreId();
    return zones.findByStoreId(storeId).stream()
        .flatMap(z -> z.getRates().stream())
        .filter(r -> matches(r, subtotalMinor, totalWeightGrams))
        .map(r -> new ShippingQuote(r.getId(), r.getName(), r.getPriceMinor(),
            r.getMinDays(), r.getMaxDays()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TaxQuote> taxQuotes(String country, String province, long taxableMinor) {
    Long storeId = TenantContext.requireStoreId();
    return taxRates.findByStoreIdAndCountry(storeId, country).stream()
        .filter(t -> t.getProvince() == null || t.getProvince().equalsIgnoreCase(province))
        .map(t -> new TaxQuote(t.getName(), taxableMinor * t.getRateBps() / 10000))
        .toList();
  }

  private boolean matches(ShippingRate r, long subtotalMinor, int weightGrams) {
    // conditions JSON evaluated minimally: flat rates always match;
    // price/weight conditions are parsed here in a full implementation
    return "flat".equals(r.getRateType());
  }
}
