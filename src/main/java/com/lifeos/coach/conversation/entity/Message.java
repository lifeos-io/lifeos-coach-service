package com.lifeos.coach.conversation.entity;

import com.lifeos.base.id.UuidV7;
import com.lifeos.coach.common.persistence.AssignedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One message of a chat, never changed once written. */
@Entity
@Table(name = "message")
public class Message extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID conversationId;
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private MessageRole role;

    private String content;
    private Instant createdAt;

    protected Message() {
    }

    public static Message of(UUID conversationId, UUID userId, MessageRole role, String content, Instant now) {
        Message message = new Message();
        message.id = UuidV7.next();
        message.conversationId = Objects.requireNonNull(conversationId);
        message.userId = Objects.requireNonNull(userId);
        message.role = Objects.requireNonNull(role);
        message.content = Objects.requireNonNull(content);
        message.createdAt = now;
        return message;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
