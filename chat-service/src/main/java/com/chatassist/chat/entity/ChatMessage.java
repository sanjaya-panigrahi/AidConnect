package com.chatassist.chat.entity;

import com.chatassist.common.model.MessageStatus;
import com.chatassist.common.model.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false)
    private String senderUsername;

    @Column(nullable = false)
    private Long receiverId;

    @Column(nullable = false)
    private String receiverUsername;

    @Column(nullable = false, length = 4000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status;

    @Column(nullable = false)
    private Instant sentAt;

    private Instant deliveredAt;

    private Instant seenAt;

    /** Set when an assistant replies to a @mention inside a user-to-user conversation.
     *  Holds the username of the other participant so the reply surfaces in both threads. */
    @Column(name = "context_username")
    private String contextUsername;

    protected ChatMessage() {
    }

    public ChatMessage(Long senderId, String senderUsername, Long receiverId, String receiverUsername, String content,
                       MessageType messageType, MessageStatus status, Instant sentAt, Instant deliveredAt) {
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.receiverId = receiverId;
        this.receiverUsername = receiverUsername;
        this.content = content;
        this.messageType = messageType;
        this.status = status;
        this.sentAt = sentAt;
        this.deliveredAt = deliveredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public String getReceiverUsername() {
        return receiverUsername;
    }

    public String getContent() {
        return content;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public void markDelivered(Instant deliveredAt) {
        this.status = MessageStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
    }

    public void markSeen(Instant seenAt) {
        this.status = MessageStatus.SEEN;
        this.seenAt = seenAt;
    }

    public String getContextUsername() {
        return contextUsername;
    }

    public void setContextUsername(String contextUsername) {
        this.contextUsername = contextUsername;
    }
}
