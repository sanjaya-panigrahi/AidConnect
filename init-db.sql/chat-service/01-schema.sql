-- ─────────────────────────────────────────────────────────────────────────────
-- chat-service / 01-schema.sql
--
-- Owns:  chat_messages
-- Engine: MySQL 8+  |  charset: utf8mb4
--
-- JPA ddl-auto=update handles column-level migrations at runtime.
-- This script guarantees indexes exist on first boot.
-- Safe to run multiple times (all statements are idempotent).
-- ─────────────────────────────────────────────────────────────────────────────

-- ── chat_messages ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS chat_messages (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    sender_id        BIGINT        NOT NULL,
    sender_username  VARCHAR(255)  NOT NULL,
    receiver_id      BIGINT        NOT NULL,
    receiver_username VARCHAR(255) NOT NULL,
    content          VARCHAR(4000) NOT NULL,
    message_type     VARCHAR(20)   NOT NULL  COMMENT 'USER or BOT',
    status           VARCHAR(20)   NOT NULL  COMMENT 'SENT, DELIVERED, or SEEN',
    sent_at          DATETIME(6)   NOT NULL,
    delivered_at     DATETIME(6)   NULL,
    seen_at          DATETIME(6)   NULL,
    context_username VARCHAR(255)  NULL      COMMENT 'Set when an assistant replies to a @mention in a user-user chat',
    PRIMARY KEY (id),
    INDEX idx_chat_conversation     (sender_username, receiver_username, sent_at),
    INDEX idx_chat_receiver_status  (receiver_username, status),
    INDEX idx_chat_context_username (context_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

