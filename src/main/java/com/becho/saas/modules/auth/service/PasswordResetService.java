package com.becho.saas.modules.auth.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.Hashing;
import com.becho.saas.modules.auth.entity.PasswordResetCode;
import com.becho.saas.modules.auth.repository.PasswordResetCodeRepository;
import com.becho.saas.modules.tenancy.entity.User;
import com.becho.saas.modules.tenancy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Forgot-password flow: 6-digit reset code via email, then code + new
 * password. Enumeration-safe: forgot-password returns the same response
 * whether or not the email belongs to a user, and rate-limits by email
 * (max 5/hour) in both cases. Codes are hash-only, 15-minute expiry,
 * single-use, max 5 attempts.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS_PER_HOUR = 5;

    private final PasswordResetCodeRepository codes;
    private final UserRepository users;
    private final MailService mail;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public PasswordResetService(PasswordResetCodeRepository codes,
                                UserRepository users,
                                MailService mail,
                                PasswordEncoder passwordEncoder,
                                AuthService authService) {
        this.codes = codes;
        this.users = users;
        this.mail = mail;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    /**
     * Sends a reset code. Always succeeds from the caller's perspective —
     * unknown emails get the same response (no enumeration).
     */
    @Transactional
    public void forgotPassword(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        // Rate limit by email regardless of user existence.
        long recent = codes.countByUserEmailAndCreatedAtAfter(
                email, LocalDateTime.now().minusHours(1));
        if (recent >= MAX_SENDS_PER_HOUR) {
            // Still return success (don't reveal rate-limit state to enumerators).
            log.warn("Password reset rate-limited for {}", mask(email));
            return;
        }
        Optional<User> user = users.findByEmail(email);
        String code = Hashing.numericCode(6);
        PasswordResetCode rec = new PasswordResetCode();
        rec.setEmail(email);
        user.ifPresent(rec::setUser);
        rec.setCodeHash(Hashing.sha256Hex(code));
        rec.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        codes.save(rec);
        if (user.isPresent()) {
            mail.sendPasswordResetCode(email, code);
        } else {
            // Unknown email: no code is actually usable, but we behave identically.
            log.info("Password reset requested for unknown email");
        }
    }

    /** Verifies the code and sets the new password (Argon2id). Single-use. */
    @Transactional
    public void resetPassword(String rawEmail, String code, String newPassword) {
        String email = normalizeEmail(rawEmail);
        assertCodeFormat(code);
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                    "Password must be at least 8 characters");
        }
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALID",
                        "Invalid or expired reset code"));
        PasswordResetCode rec = codes
                .findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALID",
                        "Invalid or expired reset code"));
        if (rec.getAttempts() >= MAX_ATTEMPTS || rec.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALID",
                    "This code is expired or has too many failed attempts. Request a new one.");
        }
        if (!Hashing.constantTimeEquals(rec.getCodeHash(), Hashing.sha256Hex(code.trim()))) {
            rec.setAttempts(rec.getAttempts() + 1);
            codes.save(rec);
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_WRONG",
                    "Wrong reset code (" + (MAX_ATTEMPTS - rec.getAttempts()) + " attempts left)");
        }
        rec.setUsedAt(LocalDateTime.now());
        codes.save(rec);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        // The password changed — kill every existing session so a compromised
        // token can't survive the reset.
        authService.revokeFamily(user.getId());
        log.info("Password reset completed for user {}", user.getId());
    }

    private String normalizeEmail(String raw) {
        if (raw == null || !raw.contains("@")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_EMAIL", "Valid email is required");
        }
        return raw.toLowerCase().trim();
    }

    private void assertCodeFormat(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_CODE",
                    "Reset code must be 6 digits");
        }
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
