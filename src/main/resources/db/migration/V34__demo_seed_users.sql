-- V34: Demo seed user for store id=1 (frontend demo mode).
-- Idempotent: INSERT IGNORE on unique keys; safe to re-run.
--
-- Credentials:  owner@store.com / owner123   (owner of demo store id=1)
--
-- Password hash is Argon2id, generated with the app's own encoder:
--   new Argon2PasswordEncoder(16, 32, 1, 1 << 14, 3)   (see SecurityConfig.passwordEncoder)
-- and self-verified: encoder.matches("owner123", hash) == true
-- using spring-security-crypto-7.1.0 + bcprov-jdk18on-1.82 from the local .m2.
--
-- Login also requires (see AuthService.login):
--   status='active', email_verified_at set, and one ACTIVE store_staff row.

INSERT IGNORE INTO users (email, password_hash, full_name, phone, email_verified_at, status, created_at, updated_at)
VALUES ('owner@store.com',
  '$argon2id$v=19$m=16384,t=3,p=1$L2VfGl9n7a8iCS9z34mr7w$2nKFRPLopEl1aukMWkAehhwKlyozdwNhf0C6rCyAxVU',
  'Demo Owner', '01700000000', NOW(3), 'active', NOW(3), NOW(3));

-- Owner membership on the demo store (login fails with NO_STORE without it).
-- Guarded on stores.id=1 so this never violates the FK if V33 was skipped.
INSERT IGNORE INTO store_staff (store_id, user_id, role, status, created_at, updated_at)
SELECT 1, u.id, 'owner', 'active', NOW(3), NOW(3)
FROM users u
WHERE u.email = 'owner@store.com'
  AND EXISTS (SELECT 1 FROM stores s WHERE s.id = 1);
