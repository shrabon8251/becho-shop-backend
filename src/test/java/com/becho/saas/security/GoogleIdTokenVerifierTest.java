package com.becho.saas.security;

import com.becho.saas.common.ApiException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Google ID token verification: fail-closed when unconfigured, malformed,
 * wrong audience, or unverified email; accepts a properly signed token.
 */
class GoogleIdTokenVerifierTest {

  private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

  private GoogleIdTokenVerifier verifier() {
    return new GoogleIdTokenVerifier(CLIENT_ID);
  }

  @Test
  void unconfiguredVerifierFailsClosedWith503() {
    GoogleIdTokenVerifier v = new GoogleIdTokenVerifier("");
    ApiException ex = assertThrows(ApiException.class, () -> v.verify("a.b.c"));
    assertEquals(503, ex.getStatus().value());
    assertEquals("GOOGLE_NOT_CONFIGURED", ex.getCode());
  }

  @Test
  void blankTokenIs400() {
    ApiException ex = assertThrows(ApiException.class, () -> verifier().verify("   "));
    assertEquals(400, ex.getStatus().value());
    assertEquals("MISSING_ID_TOKEN", ex.getCode());
  }

  @Test
  void malformedTokenIs401() {
    ApiException ex = assertThrows(ApiException.class, () -> verifier().verify("not-a-jwt"));
    assertEquals(401, ex.getStatus().value());
  }

  @Test
  void validGoogleTokenVerifiesAndNormalizesEmail() throws Exception {
    KeyPair pair = rsaPair();
    String kid = "k1";
    GoogleIdTokenVerifier v = verifier();
    v.injectKeysForTest(Map.of(kid, pair.getPublic()));

    String token = Jwts.builder()
        .header().add("kid", kid).and()
        .issuer("https://accounts.google.com")
        .audience().add(CLIENT_ID).and()
        .subject("109999999999999999999")
        .claim("email", "SomeOne@Example.COM")
        .claim("email_verified", true)
        .claim("name", "Some One")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 3_600_000))
        .signWith(pair.getPrivate(), Jwts.SIG.RS256)
        .compact();

    GoogleIdTokenVerifier.GoogleUser user = v.verify(token);
    assertEquals("someone@example.com", user.email());
    assertEquals("Some One", user.name());
  }

  @Test
  void wrongAudienceIsRejected() throws Exception {
    KeyPair pair = rsaPair();
    GoogleIdTokenVerifier v = verifier();
    v.injectKeysForTest(Map.of("k1", pair.getPublic()));

    String token = Jwts.builder()
        .header().add("kid", "k1").and()
        .issuer("https://accounts.google.com")
        .audience().add("attacker-app.apps.googleusercontent.com").and()
        .claim("email", "victim@example.com")
        .claim("email_verified", true)
        .expiration(new Date(System.currentTimeMillis() + 3_600_000))
        .signWith(pair.getPrivate(), Jwts.SIG.RS256)
        .compact();

    ApiException ex = assertThrows(ApiException.class, () -> v.verify(token));
    assertEquals(401, ex.getStatus().value());
  }

  @Test
  void unverifiedEmailIsRejected() throws Exception {
    KeyPair pair = rsaPair();
    GoogleIdTokenVerifier v = verifier();
    v.injectKeysForTest(Map.of("k1", pair.getPublic()));

    String token = Jwts.builder()
        .header().add("kid", "k1").and()
        .issuer("https://accounts.google.com")
        .audience().add(CLIENT_ID).and()
        .claim("email", "someone@example.com")
        .claim("email_verified", false)
        .expiration(new Date(System.currentTimeMillis() + 3_600_000))
        .signWith(pair.getPrivate(), Jwts.SIG.RS256)
        .compact();

    ApiException ex = assertThrows(ApiException.class, () -> v.verify(token));
    assertEquals(401, ex.getStatus().value());
    assertEquals("EMAIL_NOT_VERIFIED", ex.getCode());
  }

  @Test
  void tokenSignedByUnknownKeyIsRejected() throws Exception {
    KeyPair pair = rsaPair();
    GoogleIdTokenVerifier v = verifier();
    v.injectKeysForTest(Map.of("other-kid", pair.getPublic()));

    // Signed with pair's private key but header claims a kid we never injected
    // AND the network refresh is bypassed in tests — must fail closed.
    String token = Jwts.builder()
        .header().add("kid", "unknown-kid").and()
        .issuer("https://accounts.google.com")
        .audience().add(CLIENT_ID).and()
        .claim("email", "someone@example.com")
        .claim("email_verified", true)
        .expiration(new Date(System.currentTimeMillis() + 3_600_000))
        .signWith(pair.getPrivate(), Jwts.SIG.RS256)
        .compact();

    // refreshKeys() will try the network and fail in sandbox; must still be 401.
    ApiException ex = assertThrows(ApiException.class, () -> v.verify(token));
    assertEquals(401, ex.getStatus().value());
  }

  private static KeyPair rsaPair() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    return gen.generateKeyPair();
  }
}
