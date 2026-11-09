package com.lifeos.coach.inbound.consumer;

import com.lifeos.base.messaging.MalformedEventException;
import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.coach.recommendation.entity.RecommendationReason;
import com.lifeos.coach.recommendation.service.RecommendationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Trigger's events: a week falling behind its plan may call for a strategy revision. */
@Component
public class TriggerEventConsumer {

    private final Inbox inbox;
    private final RecommendationService recommendationService;

    public TriggerEventConsumer(Inbox inbox, RecommendationService recommendationService) {
        this.inbox = inbox;
        this.recommendationService = recommendationService;
    }

    @RetryableTopic(exclude = MalformedEventException.class, retryTopicSuffix = "-coach-retry",
            dltTopicSuffix = "-coach-dlt")
    @KafkaListener(topics = "${lifeos.coach.trigger-topic:lifeos.trigger.events.v1}", groupId = "coach")
    public void on(ConsumerRecord<String, String> record) {
        inbox.handle(record, event -> {
            if (event.is("DriftDetected", 1)) {
                JsonNode drift = event.envelope().payload();
                recommendationService.consider(event.userId(), RecommendationReason.DRIFT,
                        "At the mid-week check of " + drift.path("isoWeek").asString() + " only "
                                + drift.path("doneMinutes").asInt() + " of " + drift.path("plannedMinutes").asInt()
                                + " planned minutes are done, " + drift.path("deferredActions").asInt()
                                + " actions deferred",
                        null, null);
            }
        });
    }
}
