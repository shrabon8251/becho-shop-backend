package com.becho.saas.common;

import org.springframework.http.HttpStatus;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

/**
 * SSRF guard for outbound HTTP calls (webhooks, etc.).
 * Validates that a destination URL is a public http(s) address —
 * blocks private/loopback/link-local IPs and non-http schemes.
 */
public final class SsrfGuard {

  private SsrfGuard() {}

  /** Validates a webhook/callback destination URL. Throws ApiException if unsafe. */
  public static void validateUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_URL", "URL is required");
    }
    URI uri;
    try {
      uri = new URI(url.trim());
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_URL", "Invalid URL: " + url);
    }
    String scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_URL_SCHEME",
          "Only http/https URLs are allowed");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_URL", "URL has no host");
    }
    // Block userinfo (http://user@host) — can obscure the real host.
    if (uri.getUserInfo() != null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_URL",
          "URLs with credentials are not allowed");
    }
    // Resolve and check every IP the host maps to (DNS rebinding defense).
    InetAddress[] addrs;
    try {
      addrs = InetAddress.getAllByName(host);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_URL",
          "Cannot resolve host: " + host);
    }
    for (InetAddress addr : addrs) {
      if (isBlocked(addr)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BLOCKED_URL",
            "URL resolves to a private/internal address: " + host);
      }
    }
  }

  /** Returns true if the address is private, loopback, or link-local. */
  static boolean isBlocked(InetAddress addr) {
    if (addr.isLoopbackAddress()) return true;      // 127/8, ::1
    if (addr.isLinkLocalAddress()) return true;     // 169.254/16, fe80::/10
    if (addr.isSiteLocalAddress()) return true;     // 10/8, 172.16/12, 192.168/16, fc00::/7
    if (addr.isMulticastAddress()) return true;
    // Explicit 0.0.0.0/8 check (some JDKs don't flag 0.x as loopback).
    byte[] b = addr.getAddress();
    if (b.length == 4 && b[0] == 0) return true;
    // IPv6 documentation/reserved ranges that shouldn't be targets.
    if (addr instanceof Inet6Address) {
      // ::ffff:0:0/96 mapped private handled via site-local above; block unspecified.
      if (addr.isAnyLocalAddress()) return true;
    }
    return false;
  }
}
