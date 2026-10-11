package com.becho.saas.modules.auth.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.auth.entity.RefreshToken;
import com.becho.saas.modules.auth.repository.RefreshTokenRepository;
import com.becho.saas.modules.auth.web.AuthDtos.*;
import com.becho.saas.modules.tenancy.entity.*;
import com.becho.saas.modules.tenancy.repository.*;
import com.becho.saas.modules.auth.service.VerificationService;
import com.becho.saas.security.GoogleIdTokenVerifier;
import com.becho.saas.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Merchant authentication: registration (tenant+store+owner in one transaction),
 * login, rotating refresh tokens, logout. Email verification is enforced
 * before ANY tokens are issued — register never returns tokens.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final TenantRepository tenants;
    private final StoreRepository stores;
    private final StoreStaffRepository staff;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier googleVerifier;
    private final VerificationService verificationService;
    private final String platformAdminEmail;

    public AuthService(UserRepository users, TenantRepository tenants, StoreRepository stores,
                       StoreStaffRepository staff, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       GoogleIdTokenVerifier googleVerifier, VerificationService verificationService,
                       @Value("${becho.platform.admin-email:}") String platformAdminEmail) {
        this.users = users;
        this.tenants = tenants;
        this.stores = stores;
        this.staff = staff;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleVerifier = googleVerifier;
        this.verificationService = verificationService;
        this.platformAdminEmail = platformAdminEmail;
    }

    /**
     * Registers a new merchant account (user only). The store is NOT created
     * here — it is created later by the onboarding flow (POST /stores), so the
     * merchant chooses the name, plan and theme instead of getting an
     * auto-generated "<name>'s Store". Creating a store inside register()
     * used to produce duplicate stores: /auth/signup created one store and the
     * frontend created a second, while the JWT kept pointing at the first.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }
        User user = new User();
        user.setEmail(req.email().toLowerCase().trim());
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        if (req.termsAcceptedAt() != null) {
            user.setTermsAcceptedAt(req.termsAcceptedAt());
        }
        users.save(user);

        // Real verification email with a 6-digit code (best-effort send;
        // the code is logged server-side if SMTP is not configured).
        verificationService.sendEmailCode(user);
        return new RegisterResponse(user.getId(), user.getEmail(), true,
                "Account created. Please verify your email before logging in.");
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password"));
        if (user.isLockedOut()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
                    "Too many failed login attempts. Try again later.");
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            // Count the failure; lock the account after 5 consecutive failures.
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                user.setFailedLoginAttempts(0);
            } else {
                user.setFailedLoginAttempts(attempts);
            }
            users.save(user);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password");
        }
        // Success: clear any failure state.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is not active");
        }
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Please verify your email first");
        }

        var memberships = staff.findByUserAndStatus(user, "active");
        StoreStaff membership = null;
        if (!memberships.isEmpty()) {
            membership = req.storeId() == null ? memberships.get(0)
                    : memberships.stream().filter(m -> m.getStore().getId().equals(req.storeId()))
                    .findFirst().orElseThrow(ApiException::forbidden);

            // Trial expiry: block login with a clear message (auto-transition to expired).
            var store = membership.getStore();
            if (store.getTrialEndsAt() != null && LocalDateTime.now().isAfter(store.getTrialEndsAt())) {
                if (!"expired".equals(store.getStatus())) {
                    store.setStatus("expired");
                    stores.save(store);
                }
                throw new ApiException(HttpStatus.FORBIDDEN, "TRIAL_EXPIRED",
                        "Your 14-day trial has ended. Please subscribe to continue.");
            }
        }

        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        if (membership == null) {
            // Signed up but hasn't created a store yet (onboarding flow) —
            // issue a storeless token; the frontend routes to onboarding.
            return issueTokens(user, null, null);
        }
        return issueTokens(user, membership.getStore().getId(), membership.getRole());
    }

    /**
     * Google sign-in: the client MUST send the real Google-issued ID token
     * (from Google Identity Services). It is verified server-side — signature
     * against Google's JWKS, issuer, audience == our client id, expiry — and
     * Google must mark the email as verified. A bare {name, email} from the
     * client is never trusted (account-takeover hole).
     */
    @Transactional
    public TokenResponse loginWithGoogleIdToken(String idToken) {
        GoogleIdTokenVerifier.GoogleUser googleUser = googleVerifier.verify(idToken);
        String normalizedEmail = googleUser.email();
        User user = users.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            String displayName = googleUser.name() != null && !googleUser.name().isBlank()
                    ? googleUser.name().trim() : normalizedEmail.split("@")[0];
            String storeName = displayName + "'s Store";
            // OAuth-only account: random unusable password, never returned.
            RegisterResponse reg = register(new RegisterRequest(displayName, normalizedEmail,
                    "G00gle-" + java.util.UUID.randomUUID(), storeName));
            user = users.findById(reg.userId()).orElseThrow();
        }
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(LocalDateTime.now());
            users.save(user);
        }
        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is not active");
        }
        var memberships = staff.findByUserAndStatus(user, "active");
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        if (memberships.isEmpty()) {
            // New Google user: no store yet — the onboarding flow creates it.
            // Storeless token; the frontend routes to onboarding.
            return issueTokens(user, null, null);
        }
        var membership = memberships.get(0);
        return issueTokens(user, membership.getStore().getId(), membership.getRole());
    }

    /**
     * Rotating refresh: the presented token is revoked and replaced by a new one.
     * Reuse of an already-rotated token means theft — the whole family is revoked.
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        var claims = jwtService.parse(req.refreshToken());
        if (!"refresh".equals(claims.get("type"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_TOKEN", "Not a refresh token");
        }
        String jti = claims.get("jti", String.class);
        RefreshToken stored = (jti != null ? refreshTokens.findById(Long.parseLong(jti)) : java.util.Optional.<RefreshToken>empty())
                .orElseGet(() -> refreshTokens.findByTokenHash(sha256(req.refreshToken())).orElse(null));
        if (stored == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_TOKEN", "Refresh token unknown");
        }
        if (stored.getRevokedAt() != null) {
            // Reuse of a revoked/rotated token => possible theft: kill the family.
            revokeFamily(stored.getUser().getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_REUSED", "Refresh token reuse detected");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Refresh token expired");
        }
        User user = stored.getUser();
        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is not active");
        }
        var membershipOpt = staff.findByUserAndStatus(user, "active").stream().findFirst();

        // Rotate: revoke the old, persist the new, link them.
        stored.setRevokedAt(LocalDateTime.now());
        TokenResponse next = membershipOpt
                .map(m -> issueTokens(user, m.getStore().getId(), m.getRole()))
                .orElseGet(() -> issueTokens(user, null, null));
        // issueTokens persisted the new token; link rotation for audit
        refreshTokens.findByTokenHash(sha256(next.refreshToken())).ifPresent(nt ->
                stored.setReplacedBy(nt.getId()));
        refreshTokens.save(stored);
        return next;
    }

    /** Revoke a single refresh token (logout on one device). */
    @Transactional
    public void logout(LogoutRequest req) {
        try {
            var claims = jwtService.parse(req.refreshToken());
            String jti = claims.get("jti", String.class);
            if (jti != null) {
                refreshTokens.findById(Long.parseLong(jti)).ifPresent(t -> {
                    t.setRevokedAt(LocalDateTime.now());
                    refreshTokens.save(t);
                });
            }
        } catch (Exception ignored) {
            // Logout is best-effort for malformed tokens.
        }
    }

    /** Revoke every refresh token of the user (password change, theft). */
    @Transactional
    public void revokeFamily(Long userId) {
        // Mark all unrevoked tokens revoked; keep rows for audit.
        LocalDateTime now = LocalDateTime.now();
        refreshTokens.findByUserIdAndRevokedAtIsNull(userId).forEach(t -> {
            t.setRevokedAt(now);
            refreshTokens.save(t);
        });
    }

    /**
     * Issues tokens for a user (used by signup/verify flows).
     * Finds the user's first active store membership; a user that hasn't
     * created a store yet (onboarding) gets a storeless token.
     */
    public TokenResponse issueTokensForUser(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        return staff.findByUserAndStatus(user, "active").stream().findFirst()
                .map(m -> issueTokens(user, m.getStore().getId(), m.getRole()))
                .orElseGet(() -> issueTokens(user, null, null));
    }

    private TokenResponse issueTokens(User user, Long storeId, String role) {
        // Env-driven platform-admin bootstrap (no hardcoding). Idempotent: runs on
        // every token issuance, so changing PLATFORM_ADMIN_EMAIL moves the right.
        if (platformAdminEmail != null && !platformAdminEmail.isBlank()
                && platformAdminEmail.equalsIgnoreCase(user.getEmail())
                && !user.isPlatformAdmin()) {
            user.setPlatformAdmin(true);
            users.save(user);
        }
        String access = jwtService.accessToken(user.getId(), storeId, role, user.isPlatformAdmin());
        // Persist first so the JWT can carry the token id (jti). Use a placeholder
        // hash for the initial insert; the real hash is set right after.
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTtlSeconds()));
        rt.setIp(clientIp());
        rt.setTokenHash("pending");
        refreshTokens.save(rt);
        String refresh = jwtService.refreshToken(user.getId(), rt.getId());
        rt.setTokenHash(sha256(refresh));
        refreshTokens.save(rt);
        return new TokenResponse(access, refresh, 30 * 60, user.getId(), storeId, role);
    }

    private static String clientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String slugify(String s) {
        return s.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
