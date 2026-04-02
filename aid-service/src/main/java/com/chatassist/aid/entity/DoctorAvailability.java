package com.chatassist.aid.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_availability")
public class DoctorAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // EAGER: Doctor must be fully loaded within the @Transactional boundary so that
    // Spring's @Cacheable proxy can serialize this list to Redis after the session closes.
    // A LAZY proxy causes InvalidDefinitionException (ByteBuddyInterceptor) in Jackson.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(nullable = false)
    private boolean enabled;

    protected DoctorAvailability() {
    }

    public DoctorAvailability(Long id, Doctor doctor, LocalDateTime availableAt, boolean enabled) {
        this.id = id;
        this.doctor = doctor;
        this.availableAt = availableAt;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

