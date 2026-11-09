package com.lifeos.coach.conversation.dto;

import com.lifeos.coach.conversation.entity.MessageRole;
import java.util.List;
import java.util.UUID;

/**
 * Everything the model needs to answer one message: the user's situation, the conversation so far (oldest first,
 * the new message last) and their language.
 */
public record ChatTurn(UUID conversationId, String context, String language, List<Line> history) {

    public record Line(MessageRole role, String content) {
    }
}
