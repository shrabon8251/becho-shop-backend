-- V38: Platform admin flag on users.
-- The platform operator (super-admin) is a user-level flag, NOT a store_staff role:
-- store_staff.role is CHECK-constrained to ('owner','admin','staff','support'),
-- so ROLE_PLATFORM_ADMIN (required by PlatformController/PlatformLandingController)
-- could never be issued before. Bootstrap is env-driven via PLATFORM_ADMIN_EMAIL
-- (see AuthService) — no hardcoded emails.
ALTER TABLE users ADD COLUMN platform_admin TINYINT(1) NOT NULL DEFAULT 0;
