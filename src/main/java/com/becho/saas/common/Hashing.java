package com.becho.saas.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Small crypto helpers: SHA-256 hashing (for storing code/token hashes —
 * never store verification codes or OTPs in plaintext) and secure random
 * generation. No secrets are hardcoded here.
 */
public final class Hashing {

  private static final SecureRandom RANDOM = new SecureRandom();

  private Hashing() {}

  public static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Constant-time comparison to avoid timing side-channels on code checks. */
  public static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  /** 6-digit numeric code, e.g. for email/phone verification. */
  public static String numericCode(int digits) {
    int bound = (int) Math.pow(10, digits);
    int code = RANDOM.nextInt(bound);
    return String.format("%0" + digits + "d", code);
  }

  /** URL-safe random token (for resend links etc.). */
  public static String randomToken(int bytes) {
    byte[] buf = new byte[bytes];
    RANDOM.nextBytes(buf);
    return HexFormat.of().formatHex(buf);
  }
}
