-- ─────────────────────────────────────────────────────────────────────────────
-- aid-service / 01-schema.sql
--
-- Owns:  clinic_doctors, doctor_availability, appointment_bookings,
--        aid_conversation_state
-- Engine: MySQL 8+  |  charset: utf8mb4
--
-- JPA ddl-auto=update handles column-level migrations at runtime.
-- This script guarantees tables, indexes, and seed data exist on first boot.
-- Safe to run multiple times (all statements are idempotent).
-- ─────────────────────────────────────────────────────────────────────────────

-- ── clinic_doctors ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS clinic_doctors (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         VARCHAR(100)  NOT NULL,
    display_name VARCHAR(255)  NOT NULL,
    specialty    VARCHAR(100)  NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_clinic_doctors_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── doctor_availability ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS doctor_availability (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    doctor_id    BIGINT      NOT NULL,
    available_at DATETIME(6) NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctor_slot (doctor_id, available_at),
    KEY idx_doctor_availability_lookup (doctor_id, enabled, available_at),
    CONSTRAINT fk_da_doctor
        FOREIGN KEY (doctor_id) REFERENCES clinic_doctors(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── appointment_bookings ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS appointment_bookings (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    doctor_id        BIGINT       NOT NULL,
    patient_username VARCHAR(255) NOT NULL,
    appointment_time DATETIME(6)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appointment_slot (doctor_id, appointment_time),
    KEY idx_appointment_patient (patient_username),
    CONSTRAINT fk_ab_doctor
        FOREIGN KEY (doctor_id) REFERENCES clinic_doctors(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── aid_conversation_state ───────────────────────────────────────────────────
-- Tracks per-user multi-turn conversation state for the appointment assistant.
-- pending_options: comma-separated IDs of the numbered option list shown.
CREATE TABLE IF NOT EXISTS aid_conversation_state (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    username        VARCHAR(255)  NOT NULL,
    stage           VARCHAR(40)   NOT NULL,
    doctor_id       BIGINT        NULL,
    requested_slot  DATETIME(6)   NULL,
    proposed_slot   DATETIME(6)   NULL,
    pending_options VARCHAR(1024) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_aid_conv_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Seed: doctor directory ────────────────────────────────────────────────────
INSERT INTO clinic_doctors (code, display_name, specialty, active)
VALUES
    ('X', 'Dr X', 'General Medicine', TRUE),
    ('Y', 'Dr Y', 'Cardiology',       TRUE),
    ('Z', 'Dr Z', 'Dermatology',      TRUE)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    specialty    = VALUES(specialty),
    active       = VALUES(active);

-- ── Seed: availability slots ─────────────────────────────────────────────────
-- Dr X: tomorrow 9 AM, 11 AM, 2 PM, 4 PM
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00:00'), TRUE FROM clinic_doctors WHERE code = 'X';
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:00:00'), TRUE FROM clinic_doctors WHERE code = 'X';
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00'), TRUE FROM clinic_doctors WHERE code = 'X';
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '16:00:00'), TRUE FROM clinic_doctors WHERE code = 'X';

-- Dr Y: tomorrow 10 AM, 1 PM | day after tomorrow 9 AM, 3 PM
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00:00'), TRUE FROM clinic_doctors WHERE code = 'Y';
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '13:00:00'), TRUE FROM clinic_doctors WHERE code = 'Y';
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00:00'), TRUE FROM clinic_doctors WHERE code = 'Y';
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00:00'), TRUE FROM clinic_doctors WHERE code = 'Y';

-- Dr Z: day after tomorrow 10 AM, 2 PM | +3 days 11 AM
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '10:00:00'), TRUE FROM clinic_doctors WHERE code = 'Z';
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:00:00'), TRUE FROM clinic_doctors WHERE code = 'Z';
INSERT IGNORE INTO doctor_availability (doctor_id, available_at, enabled)
SELECT id, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00'), TRUE FROM clinic_doctors WHERE code = 'Z';

