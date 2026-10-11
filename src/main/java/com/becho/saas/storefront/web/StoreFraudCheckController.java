package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.fraud.FraudCheckService;
import com.becho.saas.modules.settings.SettingsCryptoService;
import com.becho.saas.modules.settings.StoreSettingsCache;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Fraud check: {@code POST /stores/{storeId}/fraud-check}.
 *
 * <p>Merchant-authenticated. Reads the merchant's courier API credentials
 * from store settings (server-side only — keys never reach the browser),
 * queries configured couriers for the phone's parcel history, and merges
 * the results with a risk rating.
 *
 * <p>When no courier is configured, returns 503 with {@code live:false} so
 * the frontend shows demo data instead of a fake "live" claim.
 * Rate-limited (fraud-check bucket) — phone numbers are abuse-sensitive.
 */
@RestController
@RequestMapping("/stores/{storeId}/fraud-check")
public class StoreFraudCheckController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FraudCheckService fraudCheck;
  private final StoreRepository stores;
  private final StoreScope scope;
  private final SettingsCryptoService crypto;
  private final StoreSettingsCache settingsCache;

  public StoreFraudCheckController(FraudCheckService fraudCheck,
                                   StoreRepository stores,
                                   StoreScope scope,
                                   SettingsCryptoService crypto,
                                   StoreSettingsCache settingsCache) {
    this.fraudCheck = fraudCheck;
    this.stores = stores;
    this.scope = scope;
    this.crypto = crypto;
    this.settingsCache = settingsCache;
  }

  public record FraudCheckRequest(@NotBlank String phone) {}

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
  public Map<String, Object> check(@PathVariable String storeId,
                                   @Valid @RequestBody FraudCheckRequest req) {
    Long sid = scope.require(storeId);
    String phone = FraudCheckService.normalizePhone(req.phone());
    Store store = stores.findById(sid).orElseThrow(() -> ApiException.notFound("Store"));

    Map<String, Map<String, String>> creds = extractCourierCreds(settingsCache.getSettings(sid), crypto);
    FraudCheckService.FraudResult result = fraudCheck.check(phone, creds);
    if (result == null) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NO_COURIER_CONFIGURED",
          "No courier API is configured for fraud checks");
    }
    Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
    resultMap.put("phone", result.phone());
    resultMap.put("totalParcels", result.totalParcels());
    resultMap.put("delivered", result.delivered());
    resultMap.put("cancelled", result.cancelled());
    resultMap.put("returned", result.returned());
    resultMap.put("successRate", result.successRate());
    resultMap.put("risk", result.risk());
    resultMap.put("firstSeen", result.firstSeen() == null ? "" : result.firstSeen());
    resultMap.put("lastSeen", result.lastSeen() == null ? "" : result.lastSeen());
    resultMap.put("couriers", result.couriers());
    resultMap.put("checkedAt", result.checkedAt());
    return Map.of("result", resultMap, "live", true);
  }

  /**
   * Extracts {@code settings.couriers: { courierId: { on, creds: {...} } } }
   * for couriers that are enabled. Credentials are decrypted server-side.
   */
  static Map<String, Map<String, String>> extractCourierCreds(
      String settingsJson, SettingsCryptoService crypto) {
    Map<String, Map<String, String>> out = new HashMap<>();
    if (settingsJson == null || settingsJson.isBlank()) {
      return out;
    }
    try {
      JsonNode root = MAPPER.readTree(settingsJson);
      JsonNode couriers = root.path("couriers");
      if (!couriers.isObject()) {
        return out;
      }
      for (Map.Entry<String, JsonNode> e : couriers.properties()) {
        JsonNode cfg = e.getValue();
        if (cfg.path("on").asBoolean(false)) {
          JsonNode creds = cfg.path("creds");
          if (creds.isObject()) {
            Map<String, String> m = new HashMap<>();
            for (Map.Entry<String, JsonNode> ce : creds.properties()) {
              String v = ce.getValue().asText("");
              // Decrypt if encrypted; plaintext passes through for old data.
              String plain = crypto != null ? crypto.decryptValue(v) : v;
              m.put(ce.getKey(), plain == null ? "" : plain);
            }
            out.put(e.getKey(), m);
          }
        }
      }
    } catch (Exception ignored) {
      // Malformed settings -> no couriers configured.
    }
    return out;
  }
}
