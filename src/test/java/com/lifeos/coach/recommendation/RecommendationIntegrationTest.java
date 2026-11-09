package com.lifeos.coach.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifeos.coach.support.EventSchemas;
import com.lifeos.coach.support.IntegrationTest;
import com.lifeos.coach.support.StrategyPayloads;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Proposals: when Coach asks the model, what it publishes, and what it refuses to publish. */
class RecommendationIntegrationTest extends IntegrationTest {

    private StrategyPayloads payloads;
    private UUID user;
    private UUID goal;
    private UUID phase;

    @BeforeEach
    void aUserWithAGoalAndAStrategy() {
        payloads = new StrategyPayloads(jsonMapper);
        fakeChatModel.reset();
        clock.set(Instant.parse("2026-11-09T10:00:00Z"));
        user = UUID.randomUUID();
        goal = UUID.randomUUID();
        phase = UUID.randomUUID();
        strategyEvent(user, "UserProfileUpdated", payloads.profile("Onurcan", "tr-TR"));
        strategyEvent(user, "GoalChanged", payloads.goal(goal, "In-house backend role", 0));
        strategyEvent(user, "StrategyRevised", payloads.strategy(goal, 1, phase));
    }

    @Test
    void feedbackWorthActingOnBecomesAValidProposal() {
        fakeChatModel.respondWith(advice(phase.toString(), "2026-12-01", "2027-02-28", 55, 30, 15));

        strategyEvent(user, "FeedbackRecorded",
                payloads.feedback("Interviews ask for system design, practise it", "HIGH"));

        List<JsonNode> proposals = outboxPayloads(user, "RecommendationProposed");
        assertEquals(1, proposals.size());
        JsonNode proposal = proposals.getFirst();
        EventSchemas.assertValidPayload("coach", "RecommendationProposed", 1, proposal);
        assertEquals(goal.toString(), proposal.get("goalId").asString());
        assertEquals(1, proposal.get("basedOnVersionNo").asInt());
        assertEquals("FEEDBACK", proposal.at("/sources/0/type").asString());
        assertEquals(phase.toString(), proposal.at("/revision/phases/0/phaseKey").asString());
        assertTrue(proposal.at("/revision/phases/1/phaseKey").isNull());
        assertEquals("Mülakatlar system design soruyor; bunun için bir faz ekledim.",
                proposal.get("rationale").asString());
        assertEquals("PENDING", jdbcClient.sql("SELECT status FROM recommendation WHERE id = :id")
                .param("id", UUID.fromString(proposal.get("recommendationId").asString()))
                .query(String.class).single());

        // What the model was asked: the situation, what happened, the goal, in the user's language.
        String prompt = fakeChatModel.prompts().getFirst().getContents();
        assertTrue(prompt.contains("\"In-house backend role\""), prompt);
        assertTrue(prompt.contains("Interviews ask for system design, practise it"), prompt);
        assertTrue(prompt.contains("current strategy version 1"), prompt);
        assertTrue(prompt.contains("in the user's language: tr-TR"), prompt);
    }

    @Test
    void lowReliabilityFeedbackIsNotWorthAModelCall() {
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("Maybe learn Rust?", "LOW"));

        assertEquals(List.of(), fakeChatModel.prompts());
    }

    @Test
    void noChangeABrokenRevisionOrAFailingModelProposesNothing() {
        fakeChatModel.respondWith("""
                {"change": false, "rationale": "Plan hâlâ uygun.", "revision": null}
                """);
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("Good work", "HIGH"));

        // Effort adds up to 90, a phase key from nowhere, a phase after the goal ends.
        fakeChatModel.respondWith(advice(UUID.randomUUID().toString(), "2027-05-01", "2027-12-31", 50, 30, 10));
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("Change everything", "HIGH"));

        fakeChatModel.failWith(new IllegalStateException("OpenAI is down (test)"));
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("Something", "MEDIUM"));

        assertEquals(3, fakeChatModel.prompts().size());
        assertEquals(List.of(), outboxPayloads(user, "RecommendationProposed"));
        assertEquals(0L, jdbcClient.sql("SELECT count(*) FROM recommendation WHERE user_id = :u").param("u", user)
                .query(Long.class).single());
        // The events themselves were handled (their facts are kept).
        assertEquals(3L, jdbcClient.sql("SELECT count(*) FROM context_fact WHERE user_id = :u AND kind = 'FEEDBACK'")
                .param("u", user).query(Long.class).single());
    }

    @Test
    void oneProposalAtATimeAndNotTooOften() {
        fakeChatModel.respondWith(advice(phase.toString(), "2026-12-01", "2027-02-28", 55, 30, 15));
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("First", "HIGH"));
        UUID first = UUID.fromString(outboxPayloads(user, "RecommendationProposed").getFirst()
                .get("recommendationId").asString());

        // Pending: the model is not even asked.
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("Second", "HIGH"));
        assertEquals(1, fakeChatModel.prompts().size());

        // Rejected, but within the cool-down.
        strategyEvent(user, "ProposalDecided", payloads.proposalDecided(first, goal, "REJECTED", null));
        clock.set(clock.instant().plus(Duration.ofDays(6)));
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("Third", "HIGH"));
        assertEquals(1, fakeChatModel.prompts().size());

        clock.set(clock.instant().plus(Duration.ofDays(2)));
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("Fourth", "HIGH"));
        assertEquals(2, fakeChatModel.prompts().size());
        assertEquals(2, outboxPayloads(user, "RecommendationProposed").size());
    }

    @Test
    void aWeeksReviewAndDriftAlsoAskTheModel() {
        fakeChatModel.respondWith(advice(phase.toString(), "2026-12-01", "2027-02-28", 55, 30, 15));
        triggerEvent(user, "DriftDetected", jsonMapper.readTree("""
                {"weeklyPlanId": "%s", "isoWeek": "2026-W46", "kind": "BEHIND_PACE", "capacityMinutes": 300,
                 "plannedMinutes": 240, "doneMinutes": 100, "thresholdMinutes": 120, "deferredActions": 2,
                 "checkedAt": "2026-11-12T15:00:00Z"}
                """.formatted(UUID.randomUUID())));

        JsonNode drift = outboxPayloads(user, "RecommendationProposed").getFirst();
        assertEquals(0, drift.get("sources").size());
        assertTrue(fakeChatModel.prompts().getFirst().getContents()
                .contains("only 100 of 240 planned minutes are done, 2 actions deferred"));

        UUID other = UUID.randomUUID();
        strategyEvent(other, "GoalChanged", payloads.goal(UUID.randomUUID(), "Another goal", 0));
        // No strategy yet: nothing to revise, the model is not asked.
        strategyEvent(other, "WeeklyReviewCompleted", payloads.weeklyReviewCompleted(UUID.randomUUID(), "2026-W45"));
        assertEquals(1, fakeChatModel.prompts().size());
    }

    /** The model's structured answer proposing a revision: the current phase kept, one new phase. */
    private static String advice(String keptKey, String newStarts, String newEnds, int technical, int domain,
            int other) {
        return """
                {"change": true,
                 "rationale": "Mülakatlar system design soruyor; bunun için bir faz ekledim.",
                 "revision": {
                   "summary": "Önce portföy, ama mülakat pratiğine erken başla",
                   "changeReason": "Yönetici geri bildirimi",
                   "phases": [
                     {"phaseKey": "%s", "title": "Temeli kur", "startsOn": "2026-09-01", "endsOn": "2026-11-30",
                      "exitCriteria": null, "status": "ACTIVE"},
                     {"phaseKey": null, "title": "System design pratiği", "startsOn": "%s", "endsOn": "%s",
                      "exitCriteria": "Üç deneme mülakatı", "status": "UPCOMING"}],
                   "effortAllocation": {"TECHNICAL": %d, "DOMAIN": %d, "OTHER": %d}}}
                """.formatted(keptKey, newStarts, newEnds, technical, domain, other);
    }
}
