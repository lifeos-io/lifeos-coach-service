package com.lifeos.coach.conversation.mapper;

import com.lifeos.coach.conversation.dto.ConversationResponse;
import com.lifeos.coach.conversation.dto.MessageResponse;
import com.lifeos.coach.conversation.entity.Conversation;
import com.lifeos.coach.conversation.entity.Message;
import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

    public ConversationResponse toResponse(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    public MessageResponse toResponse(Message message) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
