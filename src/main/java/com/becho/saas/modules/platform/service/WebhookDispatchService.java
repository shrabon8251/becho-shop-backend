package com.becho.saas.modules.platform.service;

import com.becho.saas.common.SsrfGuard;
import com.becho.saas.modules.platform.entity.Webhook;
import com.becho.saas.modules.platform.repository.WebhookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Delivers store webhooks. Payloads are HMAC-SHA256 signed so receivers
 * can verify authenticity. Verification protocol (documented for integrators):
 *   signature = HMAC-SHA256(payload, SHA256(raw_secret))
 * i.e. receivers hash their secret once, then verify the HMAC. The DB only
 * ever holds the SHA-256 of the secret — never the raw secret — so a DB
 * read alone cannot mint valid signatures for new secrets.
 * Async with failure counting; a webhook that keeps failing is auto-disabled
 * after 25 consecutive failures.
 */
@Service
public class WebhookDispatchService {

  private static final Logger log = LoggerFactory.getLogger(WebhookDispatchService.class);
  private static final int MAX_FAILURES = 25;
  private static final int MAX_REDIRECTS = 5;

  private final WebhookRepository webhooks;
  private final RestTemplate rest;

  public WebhookDispatchService(WebhookRepository webhooks) {
    this.webhooks = webhooks;
    // Don't follow redirects automatically — each hop is SSRF-validated manually.
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    this.rest = new RestTemplate(factory);
  }

  @Async
  public void emit(Long storeId, String topic, String payloadJson) {
    for (Webhook w : webhooks.findByStoreIdAndTopicAndStatus(storeId, topic, "active")) {
      deliver(w, topic, payloadJson);
    }
  }

  private void deliver(Webhook w, String topic, String payload) {
    try {
      // SSRF: validate the destination before every dispatch (DNS may change).
      SsrfGuard.validateUrl(w.getTargetUrl());
      String signature = hmacSha256(payload, w.getSecretHash());
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Becho-Topic", topic);
      headers.set("X-Becho-Signature", "sha256=" + signature);
      var entity = new HttpEntity<>(payload, headers);
      ResponseEntity<String> res = postWithRedirectCheck(w.getTargetUrl(), entity);
      if (res.getStatusCode().is2xxSuccessful()) {
        if (w.getFailureCount() > 0) {
          w.setFailureCount(0);
          webhooks.save(w);
        }
      } else {
        recordFailure(w);
      }
    } catch (Exception e) {
      log.warn("Webhook {} delivery failed: {}", w.getId(), e.getMessage());
      recordFailure(w);
    }
  }

  /**
   * POSTs without auto-redirect; follows up to MAX_REDIRECTS hops,
   * SSRF-validating each Location before following it.
   */
  private ResponseEntity<String> postWithRedirectCheck(String url, HttpEntity<String> entity) {
    String current = url;
    for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
      ResponseEntity<String> res = rest.postForEntity(URI.create(current), entity, String.class);
      if (!res.getStatusCode().is3xxRedirection()) return res;
      String location = res.getHeaders().getFirst(HttpHeaders.LOCATION);
      if (location == null || location.isBlank()) return res;
      // Resolve relative redirects against the current URL, then validate.
      String next = URI.create(current).resolve(location).toString();
      SsrfGuard.validateUrl(next);
      log.debug("Webhook following redirect hop {} -> {}", hop + 1, next);
      current = next;
    }
    throw new IllegalStateException("Too many redirects");
  }

  private void recordFailure(Webhook w) {
    w.setFailureCount(w.getFailureCount() + 1);
    if (w.getFailureCount() >= MAX_FAILURES) {
      w.setStatus("disabled");
      log.warn("Webhook {} auto-disabled after {} failures", w.getId(), MAX_FAILURES);
    }
    webhooks.save(w);
  }

  private static String hmacSha256(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
