package com.lifeos.coach.conversation.dto;

import com.lifeos.coach.conversation.entity.MessageRole;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, MessageRole role, String content, Instant createdAt) {
}
