-- V21: account lockout columns for brute-force protection.

ALTER TABLE users
  ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0
    COMMENT 'Consecutive failed logins; reset on success',
  ADD COLUMN locked_until DATETIME(3) NULL
    COMMENT 'Account locked until this time after 5 failures';
