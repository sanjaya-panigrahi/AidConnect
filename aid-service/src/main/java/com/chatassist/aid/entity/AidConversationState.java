package com.chatassist.aid.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "aid_conversation_state")
public class AidConversationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 255)
    private String username;

    @Column(name = "stage", nullable = false, length = 40)
    private String stage;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "requested_slot")
    private LocalDateTime requestedSlot;

    @Column(name = "proposed_slot")
    private LocalDateTime proposedSlot;

    @Column(name = "pending_options", length = 1024)
    private String pendingOptions;

    protected AidConversationState() {
    }

    public AidConversationState(String username, String stage) {
        this.username = username;
        this.stage = stage;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }

    public LocalDateTime getRequestedSlot() {
        return requestedSlot;
    }

    public void setRequestedSlot(LocalDateTime requestedSlot) {
        this.requestedSlot = requestedSlot;
    }

    public LocalDateTime getProposedSlot() {
        return proposedSlot;
    }

    public void setProposedSlot(LocalDateTime proposedSlot) {
        this.proposedSlot = proposedSlot;
    }

    public String getPendingOptions() {
        return pendingOptions;
    }

    public void setPendingOptions(String pendingOptions) {
        this.pendingOptions = pendingOptions;
    }

    public void resetToGreeting() {
        this.stage = "CHATTING";
        this.doctorId = null;
        this.requestedSlot = null;
        this.proposedSlot = null;
        this.pendingOptions = null;
    }
}

