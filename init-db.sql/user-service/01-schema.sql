-- ─────────────────────────────────────────────────────────────────────────────
-- user-service / 01-schema.sql
--
-- Owns:  users, user_activity_log
-- Engine: MySQL 8+  |  charset: utf8mb4
--
-- JPA ddl-auto=update handles column-level migrations at runtime.
-- This script guarantees indexes and initial bot entries exist on first boot.
-- Safe to run multiple times (all statements are idempotent).
-- ─────────────────────────────────────────────────────────────────────────────

-- ── users ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    first_name  VARCHAR(255)  NOT NULL,
    last_name   VARCHAR(255)  NOT NULL,
    username    VARCHAR(255)  NOT NULL,
    password    VARCHAR(255)  NOT NULL,
    email       VARCHAR(255)  NOT NULL,
    bot         BIT(1)        NOT NULL DEFAULT 0,
    online      BIT(1)        NOT NULL DEFAULT 0,
    last_active DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email    (email)
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
    INDEX idx_ual_event_type    (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

