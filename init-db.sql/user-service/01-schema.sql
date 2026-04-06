-- ─────────────────────────────────────────────────────────────────────────────
-- user-service / 01-schema.sql
--
-- Owns: users, user_credentials, user_activity_log, auth_audit_log
-- Engine: MySQL 8+  |  charset: utf8mb4
--
-- JPA ddl-auto=update handles column-level migrations at runtime.
-- This script guarantees baseline tables/indexes exist on first boot.
-- Safe to run multiple times (all statements are idempotent).
-- ─────────────────────────────────────────────────────────────────────────────

-- ── users ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    first_name  VARCHAR(255)  NOT NULL,
    last_name   VARCHAR(255)  NOT NULL,
    username    VARCHAR(255)  NOT NULL,
    email       VARCHAR(255)  NOT NULL,
    bot         BIT(1)        NOT NULL DEFAULT 0,
    online      BIT(1)        NOT NULL DEFAULT 0,
    last_active DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email    (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── user_credentials ─────────────────────────────────────────────────────────
-- Dedicated auth credential table; used by login/register flow in user-service.
CREATE TABLE IF NOT EXISTS user_credentials (
    user_id               BIGINT        NOT NULL,
    password_hash         VARCHAR(255)  NOT NULL,
    password_algo         VARCHAR(32)   NOT NULL DEFAULT 'bcrypt',
    password_changed_at   DATETIME(6)   NULL,
    failed_attempt_count  INT           NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)   NULL,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_credentials_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    INDEX idx_user_credentials_locked_until (locked_until),
    INDEX idx_user_credentials_algo (password_algo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── user_activity_log ────────────────────────────────────────────────────────
-- Persists per-user LOGIN/LOGOUT events for daily session tracking.
CREATE TABLE IF NOT EXISTS user_activity_log (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    username      VARCHAR(255)  NOT NULL,
    event_type    VARCHAR(10)   NOT NULL  COMMENT 'LOGIN or LOGOUT',
    event_time    DATETIME(6)   NOT NULL,
    activity_date DATE          NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ual_username_date (username, activity_date),
    INDEX idx_ual_event_type    (event_type),
    INDEX idx_ual_date_type_username (activity_date, event_type, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── auth_audit_log ───────────────────────────────────────────────────────────
-- Security/audit-focused auth event trail kept separate from activity dashboard.
CREATE TABLE IF NOT EXISTS auth_audit_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NULL,
    username        VARCHAR(255)  NULL,
    event_type      VARCHAR(32)   NOT NULL COMMENT 'LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT',
    event_time      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    client_ip_hash  VARCHAR(255)  NULL,
    user_agent_hash VARCHAR(255)  NULL,
    reason_code     VARCHAR(64)   NULL,
    meta_json       JSON          NULL,
    PRIMARY KEY (id),
    INDEX idx_aal_user_time (user_id, event_time),
    INDEX idx_aal_username_time (username, event_time),
    INDEX idx_aal_event_time (event_type, event_time),
    CONSTRAINT fk_auth_audit_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

