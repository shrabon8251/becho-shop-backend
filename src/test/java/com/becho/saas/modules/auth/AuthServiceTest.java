package com.becho.saas.modules.auth;

import com.becho.saas.BaseIntegrationTest;
import com.becho.saas.modules.auth.web.AuthDtos.LoginRequest;
import com.becho.saas.modules.auth.web.AuthDtos.RegisterRequest;
import com.becho.saas.modules.auth.service.AuthService;
import com.becho.saas.common.ApiException;
import com.becho.saas.modules.tenancy.repository.UserRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("needs live MySQL — enable when DB is up")
class AuthServiceTest extends BaseIntegrationTest {

    @Autowired AuthService authService;
    @Autowired UserRepository users;

    @Test
    void registerCreatesUserWithoutStore() {
        // register() creates the user account only; the store is created later
        // by the onboarding flow (POST /stores), never auto-generated here.
        var res = authService.register(new RegisterRequest(
                "Owner One", "owner1@test.com", "Secret123!", "Test Store One"));
        assertNotNull(res.userId());
        assertEquals("owner1@test.com", res.email());
        var user = users.findByEmail("owner1@test.com").orElseThrow();
        assertFalse(user.isEmailVerified());
    }

    @Test
    void loginSucceedsStorelessAfterVerification() {
        // A verified user with no store yet can log in; the token carries no
        // store_id and the frontend routes them to onboarding.
        authService.register(new RegisterRequest("Nostore", "nostore@test.com", "Secret123!", "No Store"));
        var user = users.findByEmail("nostore@test.com").orElseThrow();
        user.setEmailVerifiedAt(java.time.LocalDateTime.now());
        users.save(user);
        var tokens = authService.login(new LoginRequest("nostore@test.com", "Secret123!", null));
        assertNotNull(tokens.accessToken());
        assertNull(tokens.storeId());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        authService.register(new RegisterRequest("Dup", "dup@test.com", "Secret123!", "Dup Store"));
        assertThrows(ApiException.class, () ->
                authService.register(new RegisterRequest("Dup", "dup@test.com", "Secret123!", "Dup Store")));
    }

    @Test
    void loginBlockedUntilEmailVerified() {
        authService.register(new RegisterRequest("Unv", "unverified@test.com", "Secret123!", "Unv Store"));
        var ex = assertThrows(ApiException.class, () ->
                authService.login(new LoginRequest("unverified@test.com", "Secret123!", null)));
        assertEquals("EMAIL_NOT_VERIFIED", ex.getCode());
    }

    @Test
    void loginSucceedsAfterVerification() {
        authService.register(new RegisterRequest("Ver", "verified@test.com", "Secret123!", "Ver Store"));
        var user = users.findByEmail("verified@test.com").orElseThrow();
        user.setEmailVerifiedAt(java.time.LocalDateTime.now());
        users.save(user);
        var tokens = authService.login(new LoginRequest("verified@test.com", "Secret123!", null));
        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());
    }

    @Test
    void loginRejectsWrongPassword() {
        authService.register(new RegisterRequest("Wp", "wrongpw@test.com", "Secret123!", "Wp Store"));
        var user = users.findByEmail("wrongpw@test.com").orElseThrow();
        user.setEmailVerifiedAt(java.time.LocalDateTime.now());
        users.save(user);
        var ex = assertThrows(ApiException.class, () ->
                authService.login(new LoginRequest("wrongpw@test.com", "Nope123!", null)));
        assertEquals("BAD_CREDENTIALS", ex.getCode());
    }
}
