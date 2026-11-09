package com.lifeos.coach.inbound.consumer;

import com.lifeos.base.messaging.MalformedEventException;
import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.coach.account.service.AccountDeletionService;
import com.lifeos.coach.context.service.ContextService;
import com.lifeos.coach.recommendation.entity.RecommendationReason;
import com.lifeos.coach.recommendation.entity.RecommendationStatus;
import com.lifeos.coach.recommendation.service.RecommendationService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Strategy's events: every one goes into what Coach knows about the user; the decision on a proposal and the
 * deletion of the user are handled on top. Payloads are read as JSON, Coach keeps them as they are.
 */
@Component
public class StrategyEventConsumer {

    private final Inbox inbox;
    private final ContextService contextService;
    private final RecommendationService recommendationService;
    private final AccountDeletionService accountDeletionService;

    public StrategyEventConsumer(Inbox inbox, ContextService contextService,
            RecommendationService recommendationService, AccountDeletionService accountDeletionService) {
        this.inbox = inbox;
        this.contextService = contextService;
        this.recommendationService = recommendationService;
        this.accountDeletionService = accountDeletionService;
    }

    /** Feedback worth acting on, and a week's review, may call for a strategy revision. */
    private void considerProposal(UUID userId, String eventType, JsonNode payload) {
        if (eventType.equals("FeedbackRecorded")
                && Set.of("HIGH", "MEDIUM").contains(payload.path("reliability").asString())) {
            recommendationService.consider(userId, RecommendationReason.FEEDBACK,
                    "The user recorded feedback from " + payload.path("sourceKind").asString() + " (reliability "
                            + payload.path("reliability").asString() + "): \"" + payload.path("content").asString()
                            + "\"",
                    "FEEDBACK", UUID.fromString(payload.path("feedbackId").asString()));
        } else if (eventType.equals("WeeklyReviewCompleted")) {
            JsonNode actions = payload.path("actions");
            recommendationService.consider(userId, RecommendationReason.WEEKLY_REVIEW,
                    "The user reviewed week " + payload.path("isoWeek").asString() + ": done "
                            + actions.path("doneMinutes").asInt() + " of " + actions.path("plannedMinutes").asInt()
                            + " planned minutes, energy " + payload.path("energy").asInt() + "/5, went well: "
                            + payload.path("wentWell").asString("-") + ", got stuck: "
                            + payload.path("gotStuck").asString("-") + ", lesson: "
                            + payload.path("lesson").asString("-"),
                    "WEEKLY_REVIEW", UUID.fromString(payload.path("weeklyReviewId").asString()));
        }
    }

    // Own retry and dead-letter topics: other services consume this topic with retries of their own.
    @RetryableTopic(exclude = MalformedEventException.class, retryTopicSuffix = "-coach-retry",
            dltTopicSuffix = "-coach-dlt")
    @KafkaListener(topics = "${lifeos.coach.strategy-topic:lifeos.strategy.events.v1}", groupId = "coach")
    public void on(ConsumerRecord<String, String> record) {
        inbox.handle(record, event -> {
            if (event.eventVersion() != 1) {
                return;
            }
            UUID userId = event.userId();
            JsonNode payload = event.envelope().payload();
            switch (event.eventType()) {
                case "UserDeletionRequested" -> accountDeletionService.deleteAll(userId);
                case "ProposalDecided" -> recommendationService.decided(
                        UUID.fromString(payload.path("recommendationId").asString()),
                        RecommendationStatus.valueOf(payload.path("decision").asString()),
                        payload.path("note").isNull() ? null : payload.path("note").asString(),
                        Instant.parse(payload.path("decidedAt").asString()));
                default -> {
                    contextService.record(userId, event.eventType(), payload);
                    considerProposal(userId, event.eventType(), payload);
                }
            }
        });
    }
}
