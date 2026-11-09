package com.lifeos.coach.conversation.entity;

import com.lifeos.base.id.UuidV7;
import com.lifeos.coach.common.persistence.AssignedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A chat with Coach; its messages are rows of their own (they can be many). */
@Entity
@Table(name = "conversation")
public class Conversation extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;

    protected Conversation() {
    }

    /** The title of a conversation started without one, until its first message names it. */
    public static final String UNTITLED = "…";

    /** @param title null or blank: named after the first message */
    public static Conversation start(UUID userId, String title, Instant now) {
        Conversation conversation = new Conversation();
        conversation.id = UuidV7.next();
        conversation.userId = Objects.requireNonNull(userId);
        conversation.title = title == null || title.isBlank() ? UNTITLED : title.strip();
        conversation.createdAt = now;
        conversation.updatedAt = now;
        return conversation;
    }

    /** A new message was added. */
    public void touched(Instant now) {
        this.updatedAt = now;
    }

    /** Names a conversation started without a title after its first message. */
    public void nameAfter(String firstMessage, Instant now) {
        if (!UNTITLED.equals(title)) {
            return;
        }
        String line = firstMessage.strip().lines().findFirst().orElse(UNTITLED);
        this.title = line.length() <= 60 ? line : line.substring(0, 59) + "…";
        this.updatedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
