package com.lifeos.coach.conversation.service;

import com.lifeos.coach.conversation.dto.ChatTurn;
import com.lifeos.coach.conversation.dto.ConversationResponse;
import com.lifeos.coach.conversation.dto.MessageResponse;
import java.util.List;
import java.util.UUID;

/**
 * Chats with Coach. A turn is two steps around the streamed answer: {@link #begin} stores the user's message and
 * gathers what the model needs; {@link #finish} stores the complete answer once the stream ended.
 */
public interface ConversationService {

    ConversationResponse start(UUID userId, String title);

    /** Most recently active first. */
    List<ConversationResponse> list(UUID userId);

    /** Oldest first. */
    List<MessageResponse> messages(UUID userId, UUID conversationId);

    /** Stores the user's message; 404 for another user's conversation. */
    ChatTurn begin(UUID userId, UUID conversationId, String content);

    /** Stores Coach's complete answer. */
    MessageResponse finish(UUID userId, UUID conversationId, String answer);
}
