package com.chatassist.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "assistant", nullable = false)
    private boolean bot;

    @Column(nullable = false, columnDefinition = "BIT(1) DEFAULT 0")
    private boolean online;

    @Column(name = "last_active")
    private Instant lastActive;

    protected AppUser() {
    }

    public AppUser(String firstName, String lastName, String username, String email, boolean bot) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.email = email;
        this.bot = bot;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUsername() {
        return username;
    }


    public String getEmail() {
        return email;
    }

    public boolean isBot() {
        return bot;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
        this.lastActive = Instant.now();
    }

    public Instant getLastActive() {
        return lastActive;
    }
}

