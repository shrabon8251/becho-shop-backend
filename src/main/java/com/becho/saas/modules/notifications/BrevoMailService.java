package com.becho.saas.modules.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Transactional email via Brevo REST API (HTTPS, port 443).
 *
 * <p>Why Brevo and not SMTP: Render blocks outbound SMTP ports
 * (25/465/587), so SMTP simply doesn't work in production. Brevo's
 * HTTPS API works everywhere. Free tier: 300 emails/day.
 *
 * <p>Each merchant uses their OWN Brevo API key
 * ({@code settings.brevo.apiKey}) — mail goes from their own Brevo
 * account, so SPF/DKIM, limits and reputation are per-merchant.
 * The key is encrypted at rest and never leaves the server.
 */
@Service
public class BrevoMailService {

  private static final Logger log = LoggerFactory.getLogger(BrevoMailService.class);
  private static final String API_URL = "https://api.brevo.com/v3/smtp/email";

  private final RestTemplate rest = new RestTemplate();

  /**
   * Sends an email via Brevo. Returns true on success.
   *
   * @param apiKey     Brevo API key (platform's or merchant's)
   * @param fromEmail  sender address (must be a verified sender in Brevo)
   * @param fromName   sender display name (may be null)
   * @param toEmail    recipient
   * @param subject    subject
   * @param textBody   plain-text body
   */
  public boolean send(String apiKey, String fromEmail, String fromName,
                      String toEmail, String subject, String textBody) {
    if (apiKey == null || apiKey.isBlank()
        || fromEmail == null || fromEmail.isBlank()
        || toEmail == null || toEmail.isBlank()) {
      return false;
    }
    try {
      Map<String, Object> sender = fromName == null || fromName.isBlank()
          ? Map.of("email", fromEmail)
          : Map.of("name", fromName, "email", fromEmail);
      Map<String, Object> payload = Map.of(
          "sender", sender,
          "to", List.of(Map.of("email", toEmail)),
          "subject", subject == null ? "" : subject,
          "textContent", textBody == null ? "" : textBody);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("api-key", apiKey);
      ResponseEntity<String> res = rest.postForEntity(
          API_URL, new HttpEntity<>(payload, headers), String.class);
      boolean ok = res.getStatusCode().is2xxSuccessful();
      if (!ok) {
        log.warn("Brevo rejected email to {}: {} {}", mask(toEmail),
            res.getStatusCode(), res.getBody());
      }
      return ok;
    } catch (Exception e) {
      log.warn("Brevo send failed to {}: {}", mask(toEmail), e.toString());
      return false;
    }
  }

  private String mask(String email) {
    int at = email.indexOf('@');
    if (at <= 1) return "***";
    return email.charAt(0) + "***" + email.substring(at);
  }
}
