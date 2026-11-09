package com.lifeos.coach.account.service.impl;

import com.lifeos.coach.account.service.AccountDeletionService;
import com.lifeos.coach.context.repository.ContextFactRepository;
import com.lifeos.coach.conversation.repository.ConversationRepository;
import com.lifeos.coach.conversation.repository.MessageRepository;
import com.lifeos.coach.recommendation.repository.RecommendationRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes what Coach knows (facts), proposed (recommendations) and talked about (conversations, messages). What
 * was sent to the model provider is governed by its own retention. Repeating it is harmless.
 */
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionServiceImpl.class);

    private final ContextFactRepository contextFactRepository;
    private final RecommendationRepository recommendationRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public AccountDeletionServiceImpl(ContextFactRepository contextFactRepository,
            RecommendationRepository recommendationRepository, MessageRepository messageRepository,
            ConversationRepository conversationRepository) {
        this.contextFactRepository = contextFactRepository;
        this.recommendationRepository = recommendationRepository;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    @Override
    @Transactional
    public void deleteAll(UUID userId) {
        int facts = contextFactRepository.deleteAll(userId);
        int recommendations = recommendationRepository.deleteByUserId(userId);
        int messages = messageRepository.deleteByUserId(userId);
        int conversations = conversationRepository.deleteByUserId(userId);
        log.info("Deleted the data of user {}: {} facts, {} recommendations, {} conversations, {} messages", userId,
                facts, recommendations, conversations, messages);
    }
}
