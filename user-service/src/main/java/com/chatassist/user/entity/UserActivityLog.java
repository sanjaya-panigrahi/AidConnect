package com.chatassist.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "user_activity_log",
    indexes = {
        @Index(name = "idx_ual_username_date", columnList = "username, activityDate"),
        @Index(name = "idx_ual_event_type",    columnList = "eventType")
    }
)
public class UserActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActivityEventType eventType;

    @Column(nullable = false)
    private Instant eventTime;

    /** Denormalised date for fast day-scoped aggregation queries. */
    @Column(nullable = false)
    private LocalDate activityDate;

    protected UserActivityLog() {}

    public UserActivityLog(String username, ActivityEventType eventType) {
        this.username     = username;
        this.eventType    = eventType;
        this.eventTime    = Instant.now();
        this.activityDate = LocalDate.now();
    }

    public Long getId()                     { return id; }
    public String getUsername()             { return username; }
    public ActivityEventType getEventType() { return eventType; }
    public Instant getEventTime()           { return eventTime; }
    public LocalDate getActivityDate()      { return activityDate; }
}

