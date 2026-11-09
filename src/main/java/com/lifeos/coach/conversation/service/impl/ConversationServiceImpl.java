package com.lifeos.coach.conversation.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.coach.context.service.ContextService;
import com.lifeos.coach.conversation.dto.ChatTurn;
import com.lifeos.coach.conversation.dto.ConversationResponse;
import com.lifeos.coach.conversation.dto.MessageResponse;
import com.lifeos.coach.conversation.entity.Conversation;
import com.lifeos.coach.conversation.entity.Message;
import com.lifeos.coach.conversation.entity.MessageRole;
import com.lifeos.coach.conversation.mapper.ConversationMapper;
import com.lifeos.coach.conversation.repository.ConversationRepository;
import com.lifeos.coach.conversation.repository.MessageRepository;
import com.lifeos.coach.conversation.service.ConversationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The model sees the last {@value #HISTORY} messages of a conversation, so long chats stay affordable. */
@Service
public class ConversationServiceImpl implements ConversationService {

    static final int HISTORY = 20;

    private final ConversationRepository repository;
    private final MessageRepository messageRepository;
    private final ContextService contextService;
    private final ConversationMapper conversationMapper;
    private final Clock clock;

    public ConversationServiceImpl(ConversationRepository repository, MessageRepository messageRepository,
            ContextService contextService, ConversationMapper conversationMapper, Clock clock) {
        this.repository = repository;
        this.messageRepository = messageRepository;
        this.contextService = contextService;
        this.conversationMapper = conversationMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ConversationResponse start(UUID userId, String title) {
        return conversationMapper.toResponse(repository.save(Conversation.start(userId, title, clock.instant())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> list(UUID userId) {
        return repository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(conversationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> messages(UUID userId, UUID conversationId) {
        load(userId, conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId).stream()
                .map(conversationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChatTurn begin(UUID userId, UUID conversationId, String content) {
        Conversation conversation = load(userId, conversationId);
        Instant now = clock.instant();
        messageRepository.save(Message.of(conversationId, userId, MessageRole.USER, content.strip(), now));
        conversation.nameAfter(content, now);
        conversation.touched(now);
        List<Message> all = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
        List<ChatTurn.Line> history = all.subList(Math.max(0, all.size() - HISTORY), all.size()).stream()
                .map(message -> new ChatTurn.Line(message.getRole(), message.getContent()))
                .toList();
        return new ChatTurn(conversationId, contextService.describe(userId), contextService.languageOf(userId),
                history);
    }

    @Override
    @Transactional
    public MessageResponse finish(UUID userId, UUID conversationId, String answer) {
        Conversation conversation = load(userId, conversationId);
        Instant now = clock.instant();
        Message message = messageRepository.save(Message.of(conversationId, userId, MessageRole.ASSISTANT,
                answer.length() <= 20000 ? answer : answer.substring(0, 20000), now));
        conversation.touched(now);
        return conversationMapper.toResponse(message);
    }

    private Conversation load(UUID userId, UUID conversationId) {
        return repository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Conversation not found."));
    }
}
