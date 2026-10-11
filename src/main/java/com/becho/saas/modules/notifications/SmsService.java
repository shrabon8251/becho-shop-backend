package com.becho.saas.modules.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Outbound SMS via Bangladeshi gateway HTTP APIs (server-side only).
 *
 * <p>Supported providers (merchant configures one in
 * {@code settings.notifications.sms = { provider, apiKey, senderId }}):
 * <ul>
 *   <li><b>bulksmsbd</b> — POST bulksmsbd.net/api/smsapi {api_key, senderid, number, message}</li>
 *   <li><b>sslwireless</b> — POST smsplus.sslwireless.com/api/v3/send-sms (api_token)</li>
 *   <li><b>d7</b> — POST rest-api.d7networks.com/secure/sendbatch (X-API-Key)</li>
 * </ul>
 *
 * <p>API keys live in the merchant's store settings — never exposed to the
 * browser. Sending is best-effort: failures are logged, never thrown.
 */
@Service("notificationSmsService")
public class SmsService {

  private static final Logger log = LoggerFactory.getLogger(SmsService.class);

  private final RestTemplate rest = new RestTemplate();

  /**
   * Sends an SMS. Returns true on apparent success.
   *
   * @param provider one of bulksmsbd|sslwireless|d7
   * @param apiKey   merchant's provider API key/token
   * @param senderId merchant's approved sender id (may be null)
   * @param to       BD phone, 01XXXXXXXXX or 8801XXXXXXXXX
   * @param message  plain-text body
   */
  public boolean send(String provider, String apiKey, String senderId,
                      String to, String message) {
    if (provider == null || apiKey == null || apiKey.isBlank()
        || to == null || to.isBlank() || message == null || message.isBlank()) {
      return false;
    }
    try {
      return switch (provider.trim().toLowerCase()) {
        case "bulksmsbd" -> sendBulkSmsBd(apiKey, senderId, to, message);
        case "sslwireless" -> sendSslWireless(apiKey, senderId, to, message);
        case "d7" -> sendD7(apiKey, to, message);
        default -> {
          log.warn("Unknown SMS provider: {}", provider);
          yield false;
        }
      };
    } catch (Exception e) {
      log.warn("SMS send failed via {} to {}: {}", provider, mask(to), e.toString());
      return false;
    }
  }

  private boolean sendBulkSmsBd(String apiKey, String senderId, String to, String message) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("api_key", apiKey);
    form.add("senderid", senderId == null || senderId.isBlank() ? "8809612441583" : senderId);
    form.add("number", toInternational(to));
    form.add("message", message);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    ResponseEntity<String> res = rest.postForEntity(
        "https://bulksmsbd.net/api/smsapi",
        new HttpEntity<>(form, headers), String.class);
    String body = res.getBody() == null ? "" : res.getBody();
    // 202 = submitted. Anything else is a documented error code.
    boolean ok = res.getStatusCode().is2xxSuccessful() && body.contains("202");
    if (!ok) log.warn("BulkSMSBD rejected SMS: {}", body);
    return ok;
  }

  private boolean sendSslWireless(String apiToken, String sid, String to, String message) {
    var payload = Map.of(
        "api_token", apiToken,
        "sid", sid == null ? "" : sid,
        "msisdn", toInternational(to),
        "sms", message,
        "csms_id", "becho-" + System.currentTimeMillis());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> res = rest.postForEntity(
        "https://smsplus.sslwireless.com/api/v3/send-sms",
        new HttpEntity<>(payload, headers), String.class);
    boolean ok = res.getStatusCode().is2xxSuccessful();
    if (!ok) log.warn("SSL Wireless SMS failed: {}", res.getStatusCode());
    return ok;
  }

  private boolean sendD7(String apiKey, String to, String message) {
    var payload = Map.of("messages", new Object[]{
        Map.of("channel", "sms",
            "recipients", new String[]{toInternational(to)},
            "content", message,
            "msg_type", "text",
            "data_coding", "text")
    });
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-API-Key", apiKey);
    ResponseEntity<String> res = rest.postForEntity(
        "https://rest-api.d7networks.com/secure/sendbatch",
        new HttpEntity<>(payload, headers), String.class);
    boolean ok = res.getStatusCode().is2xxSuccessful();
    if (!ok) log.warn("D7 SMS failed: {}", res.getStatusCode());
    return ok;
  }

  /** 01XXXXXXXXX -> 8801XXXXXXXXX (gateway international format). */
  static String toInternational(String to) {
    String d = to.replaceAll("\\D", "");
    if (d.startsWith("880") && d.length() == 13) return d;
    if (d.startsWith("0") && d.length() == 11) return "880" + d.substring(1);
    return d;
  }

  private String mask(String phone) {
    return phone == null || phone.length() < 4 ? "****"
        : "****" + phone.substring(phone.length() - 4);
  }
}
