package com.chatassist.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auth_audit_log")
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username")
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AuthAuditEventType eventType;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "client_ip_hash")
    private String clientIpHash;

    @Column(name = "user_agent_hash")
    private String userAgentHash;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Lob
    @Column(name = "meta_json")
    private String metaJson;

    protected AuthAuditLog() {
    }

    public AuthAuditLog(Long userId, String username, AuthAuditEventType eventType, String reasonCode) {
        this.userId = userId;
        this.username = username;
        this.eventType = eventType;
        this.eventTime = Instant.now();
        this.reasonCode = reasonCode;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public AuthAuditEventType getEventType() {
        return eventType;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public String getClientIpHash() {
        return clientIpHash;
    }

    public String getUserAgentHash() {
        return userAgentHash;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getMetaJson() {
        return metaJson;
    }
}

