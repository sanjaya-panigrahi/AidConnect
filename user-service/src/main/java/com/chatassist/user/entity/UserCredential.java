package com.chatassist.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_credentials")
public class UserCredential {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_algo", nullable = false, length = 32)
    private String passwordAlgo;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserCredential() {
    }

    public UserCredential(Long userId, String passwordHash) {
        Instant now = Instant.now();
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.passwordAlgo = "bcrypt";
        this.passwordChangedAt = now;
        this.failedAttemptCount = 0;
        this.lockedUntil = null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPasswordAlgo() {
        return passwordAlgo;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public int getFailedAttemptCount() {
        return failedAttemptCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

