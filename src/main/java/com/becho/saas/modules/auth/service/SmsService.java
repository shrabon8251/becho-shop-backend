package com.becho.saas.modules.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Outbound SMS for phone OTPs.
 *
 * <p>No SMS provider is wired yet (env: SMS_PROVIDER / SMS_API_KEY when one
 * is chosen). Until then every OTP is logged server-side — the demo reads it
 * from the logs. The verify endpoint ALWAYS checks the OTP; sending being
 * a log line never bypasses verification.
 */
@Service("authSmsService")
public class SmsService {

  private static final Logger log = LoggerFactory.getLogger(SmsService.class);

  private final String provider;

  public SmsService(@Value("${becho.sms.provider:}") String provider) {
    this.provider = provider == null ? "" : provider.trim();
  }

  public void sendOtp(String phone, String code) {
    if (provider.isEmpty()) {
      // Dev/demo fallback: log the OTP so the flow can be completed.
      log.warn("DEV-ONLY: phone OTP for {} is {}", phone, code);
      return;
    }
    // TODO: plug in the chosen provider (SSL Wireless / D7 / Twilio) here.
    log.warn("SMS provider '{}' not implemented; OTP for {} logged instead: {}",
        provider, phone, code);
  }
}
