package com.chatassist.aid.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Annotated with @JsonIgnoreProperties to prevent Jackson from failing when
 * serializing a Hibernate proxy (ByteBuddyInterceptor / hibernateLazyInitializer)
 * to Redis cache.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "clinic_doctors")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(nullable = false, length = 100)
    private String specialty;

    @Column(nullable = false)
    private boolean active;

    protected Doctor() {
    }

    public Doctor(Long id, String code, String displayName, String specialty, boolean active) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
        this.specialty = specialty;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public boolean isActive() {
        return active;
    }
}

