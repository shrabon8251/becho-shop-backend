package com.becho.saas.modules.auth.service;

import com.becho.saas.common.ApiException;
import com.becho.saas.common.Hashing;
import com.becho.saas.modules.auth.entity.PasswordResetCode;
import com.becho.saas.modules.auth.repository.PasswordResetCodeRepository;
import com.becho.saas.modules.tenancy.entity.User;
import com.becho.saas.modules.tenancy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Forgot-password: enumeration-safe behavior, code verification,
 * single-use, expiry, attempt limits.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock PasswordResetCodeRepository codes;
    @Mock UserRepository users;
    @Mock MailService mail;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthService authService;

    private PasswordResetService service;

    @BeforeEach
    void setup() {
        service = new PasswordResetService(codes, users, mail, passwordEncoder, authService);
    }

    private static void setId(User user, Long id) {
        try {
            var f = user.getClass().getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void forgotPassword_unknownEmail_stillSucceeds() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(codes.countByUserEmailAndCreatedAtAfter(eq("ghost@example.com"), any()))
                .thenReturn(0L);
        // Must not throw — same response as for a real user.
        assertDoesNotThrow(() -> service.forgotPassword("ghost@example.com"));
        // No email sent for unknown user, but a (useless) code row is stored.
        verify(mail, never()).sendPasswordResetCode(any(), any());
        verify(codes).save(any(PasswordResetCode.class));
    }

    @Test
    void forgotPassword_knownEmail_sendsCode() {
        User user = new User();
        user.setEmail("real@example.com");
        when(users.findByEmail("real@example.com")).thenReturn(Optional.of(user));
        when(codes.countByUserEmailAndCreatedAtAfter(eq("real@example.com"), any()))
                .thenReturn(0L);
        service.forgotPassword("real@example.com");
        verify(mail).sendPasswordResetCode(eq("real@example.com"), argThat(c -> c.matches("\\d{6}")));
    }

    @Test
    void forgotPassword_rateLimited_returnsSilently() {
        when(codes.countByUserEmailAndCreatedAtAfter(eq("x@example.com"), any()))
                .thenReturn(5L);
        // Still succeeds (no enumeration of rate-limit state).
        assertDoesNotThrow(() -> service.forgotPassword("x@example.com"));
        verify(codes, never()).save(any());
    }

    @Test
    void resetPassword_wrongCode_incrementsAttempts() {
        User user = new User();
        setId(user, 1L);
        user.setEmail("u@example.com");
        PasswordResetCode rec = new PasswordResetCode();
        rec.setUser(user);
        rec.setCodeHash(Hashing.sha256Hex("123456"));
        rec.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(codes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(rec));
        ApiException ex = assertThrows(ApiException.class,
                () -> service.resetPassword("u@example.com", "999999", "newpassword123"));
        assertEquals("CODE_WRONG", ex.getCode());
        assertEquals(1, rec.getAttempts());
    }

    @Test
    void resetPassword_correctCode_updatesPassword_singleUse() {
        User user = new User();
        setId(user, 1L);
        user.setEmail("u@example.com");
        PasswordResetCode rec = new PasswordResetCode();
        rec.setUser(user);
        rec.setCodeHash(Hashing.sha256Hex("123456"));
        rec.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(codes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(rec));
        when(passwordEncoder.encode("newpassword123")).thenReturn("ARGON2ID-HASH");
        service.resetPassword("u@example.com", "123456", "newpassword123");
        assertNotNull(rec.getUsedAt(), "Code must be marked used (single-use)");
        verify(users).save(argThat(u -> "ARGON2ID-HASH".equals(u.getPasswordHash())));
        verify(authService).revokeFamily(1L);
    }

    @Test
    void resetPassword_expiredCode_rejected() {
        User user = new User();
        setId(user, 1L);
        PasswordResetCode rec = new PasswordResetCode();
        rec.setUser(user);
        rec.setCodeHash(Hashing.sha256Hex("123456"));
        rec.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(codes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(rec));
        ApiException ex = assertThrows(ApiException.class,
                () -> service.resetPassword("u@example.com", "123456", "newpassword123"));
        assertEquals("CODE_INVALID", ex.getCode());
    }

    @Test
    void resetPassword_weakPassword_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.resetPassword("u@example.com", "123456", "short"));
        assertEquals("WEAK_PASSWORD", ex.getCode());
    }
}
