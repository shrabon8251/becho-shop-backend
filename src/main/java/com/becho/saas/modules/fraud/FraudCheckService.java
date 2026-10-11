package com.becho.saas.modules.fraud;

import com.becho.saas.common.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fraud check via courier parcel-history APIs (server-side only).
 *
 * <p>Currently implements Steadfast's fraud-check endpoint
 * ({@code GET /fraud_check/{phone}}), which returns total/delivered/cancelled
 * parcel counts per phone number. The provider interface allows Pathao/RedX
 * to be added later.
 *
 * <p>Credentials come from the merchant's courier settings (store settings
 * JSON) — they never leave the server. If no courier is configured, the
 * caller gets {@code live=false} so the frontend shows demo data instead
 * of a fake "live" claim.
 */
@Service
public class FraudCheckService {

  private static final Logger log = LoggerFactory.getLogger(FraudCheckService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RestTemplate rest = new RestTemplate();

  public record CourierResult(
      String courier, int total, int delivered, int cancelled, int returned) {}

  public record FraudResult(
      String phone,
      int totalParcels,
      int delivered,
      int cancelled,
      int returned,
      int successRate,
      String risk,
      String firstSeen,
      String lastSeen,
      List<String> couriers,
      long checkedAt) {}

  /**
   * Checks a phone number across all configured couriers.
   *
   * @param phone normalized BD phone (01XXXXXXXXX)
   * @param courierCreds map of courierId -> credentials (from store settings)
   * @return merged fraud result, or null when no courier is configured
   */
  public FraudResult check(String phone, Map<String, Map<String, String>> courierCreds) {
    List<CourierResult> results = new ArrayList<>();

    // Steadfast: documented fraud-check endpoint.
    Map<String, String> sf = courierCreds.get("steadfast");
    if (isConfigured(sf, "api_key", "secret_key")) {
      try {
        results.add(checkSteadfast(phone, sf.get("api_key"), sf.get("secret_key")));
      } catch (Exception e) {
        log.warn("Steadfast fraud check failed for phone ending {}", mask(phone), e);
      }
    }

    // Pathao / RedX: no public fraud-check endpoint; skip unless added later.
    // (Their parcel-history APIs require OAuth flows not suited to this check.)

    if (results.isEmpty()) {
      return null; // no courier configured or all failed -> live=false
    }
    return merge(phone, results);
  }

  private CourierResult checkSteadfast(String phone, String apiKey, String secretKey) {
    String url = "https://portal.packzy.com/api/v1/fraud_check/" + phone;
    HttpHeaders headers = new HttpHeaders();
    headers.set("Api-Key", apiKey);
    headers.set("Secret-Key", secretKey);
    ResponseEntity<String> res = rest.exchange(
        url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
      throw new IllegalStateException("Steadfast returned " + res.getStatusCode());
    }
    try {
      JsonNode root = MAPPER.readTree(res.getBody());
      // Older endpoint: { Total_parcels, total_delivered, total_cancelled, ... }
      // Newer /score endpoint: { delivery_ratio, ... } — handle both.
      int total = intAt(root, "Total_parcels", "total_parcels", 0);
      int delivered = intAt(root, "total_delivered", 0);
      int cancelled = intAt(root, "total_cancelled", 0);
      if (total == 0 && root.has("delivery_ratio") && !root.get("delivery_ratio").isNull()) {
        // Score endpoint: ratios only, no absolute counts. Estimate from volume band.
        int ratio = root.get("delivery_ratio").asInt(0);
        total = estimateFromVolumeBand(root.path("volume_band").asText(""));
        delivered = total * ratio / 100;
        cancelled = total - delivered;
      }
      return new CourierResult("steadfast", total, delivered, cancelled, 0);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse Steadfast response", e);
    }
  }

  private int estimateFromVolumeBand(String band) {
    return switch (band) {
      case "low" -> 3;       // 1-5
      case "medium" -> 13;    // 6-20
      case "high" -> 100;    // 21-200
      case "very_high" -> 250;
      default -> 0;
    };
  }

  private int intAt(JsonNode root, String key, int def) {
    return root.has(key) && root.get(key).isNumber() ? root.get(key).asInt(def) : def;
  }

  private int intAt(JsonNode root, String k1, String k2, int def) {
    int v = intAt(root, k1, def);
    return v != def ? v : intAt(root, k2, def);
  }

  FraudResult merge(String phone, List<CourierResult> results) {
    int total = results.stream().mapToInt(CourierResult::total).sum();
    int delivered = results.stream().mapToInt(CourierResult::delivered).sum();
    int cancelled = results.stream().mapToInt(CourierResult::cancelled).sum();
    int returned = results.stream().mapToInt(CourierResult::returned).sum();
    int successRate = total > 0 ? (int) Math.round(delivered * 100.0 / total) : 0;
    String risk;
    if (total == 0) {
      risk = "unknown";
    } else if (successRate >= 80) {
      risk = "low";
    } else if (successRate >= 50) {
      risk = "medium";
    } else {
      risk = "high";
    }
    List<String> couriers = results.stream().map(CourierResult::courier).toList();
    return new FraudResult(phone, total, delivered, cancelled, returned,
        successRate, risk, null, null, couriers, Instant.now().toEpochMilli());
  }

  private boolean isConfigured(Map<String, String> creds, String... keys) {
    if (creds == null) return false;
    for (String k : keys) {
      String v = creds.get(k);
      if (v == null || v.isBlank()) return false;
    }
    return true;
  }

  private String mask(String phone) {
    return phone == null || phone.length() < 4 ? "****"
        : "****" + phone.substring(phone.length() - 4);
  }

  /**
   * Normalizes a BD phone number to 01XXXXXXXXX format.
   * @throws ApiException 400 for invalid numbers
   */
  public static String normalizePhone(String raw) {
    if (raw == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PHONE", "Phone number is required");
    }
    String digits = raw.replaceAll("\\D", "");
    // +880/880 prefix -> 01XXXXXXXXX
    if (digits.startsWith("880") && digits.length() == 13) {
      digits = "0" + digits.substring(3);
    } else if (digits.length() == 10 && digits.startsWith("1")) {
      digits = "0" + digits;
    }
    if (!digits.matches("01[3-9][0-9]{8}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PHONE",
          "Invalid Bangladeshi phone number");
    }
    return digits;
  }
}
