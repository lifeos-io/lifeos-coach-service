package com.lifeos.coach.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifeos.coach.context.service.ContextService;
import com.lifeos.coach.recommendation.entity.Recommendation;
import com.lifeos.coach.recommendation.entity.RecommendationReason;
import com.lifeos.coach.recommendation.repository.RecommendationRepository;
import com.lifeos.coach.support.IntegrationTest;
import com.lifeos.coach.support.StrategyPayloads;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** What Coach keeps from Strategy's events, and how it describes the user to the model. */
class ContextIntegrationTest extends IntegrationTest {

    @Autowired
    private ContextService contextService;

    @Autowired
    private RecommendationRepository recommendationRepository;

    private StrategyPayloads payloads;

    @BeforeEach
    void payloads() {
        payloads = new StrategyPayloads(jsonMapper);
        clock.set(Instant.parse("2026-11-09T10:00:00Z"));
    }

    @Test
    void theUsersSituationIsDescribedFromTheirEvents() {
        UUID user = UUID.randomUUID();
        UUID goal = UUID.randomUUID();
        UUID phase = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        UUID decision = UUID.randomUUID();
        UUID closedDecision = UUID.randomUUID();
        strategyEvent(user, "UserProfileUpdated", payloads.profile("Onurcan", "tr-TR"));
        strategyEvent(user, "GoalChanged", payloads.goal(goal, "In-house backend role", 0));
        strategyEvent(user, "StrategyRevised", payloads.strategy(goal, 1, phase));
        strategyEvent(user, "KnowledgeItemChanged",
                payloads.knowledgeItem(goal, "Recruiters only read LinkedIn", "FALSIFIED"));
        strategyEvent(user, "WeeklyPlanActivated", payloads.weekActivated(plan, "2026-W46"));
        strategyEvent(user, "ActionStatusChanged", payloads.actionStatusChanged(plan, "2026-W46", 1, 100));
        strategyEvent(user, "FeedbackRecorded", payloads.feedback("Practise system design", "HIGH"));
        strategyEvent(user, "EvidenceRecorded", payloads.evidence("lifeos-contracts 0.6.0 released"));
        strategyEvent(user, "DecisionRecorded", payloads.decisionRecorded(decision, "Where to get K8s experience"));
        strategyEvent(user, "DecisionRecorded", payloads.decisionRecorded(closedDecision, "An old decision"));
        strategyEvent(user, "DecisionClosed", payloads.decisionClosed(closedDecision));
        strategyEvent(user, "IdeaChanged", payloads.idea("A Life OS CLI", "ACTIVE"));
        strategyEvent(user, "IdeaChanged", payloads.idea("Learn Go", "INBOX"));
        strategyEvent(user, "WeeklyReviewCompleted", payloads.weeklyReviewCompleted(UUID.randomUUID(), "2026-W45"));
        // A follow-up event, merged into the week Coach already knows.
        strategyEvent(user, "CapacityChanged", jsonMapper.readTree("""
                {"weeklyPlanId": "%s", "isoWeek": "2026-W46", "previousCapacityMinutes": 300, "capacityMinutes": 270}
                """.formatted(plan)));

        String described = contextService.describe(user);

        for (String expected : new String[] {
                "Profile: Onurcan, language tr-TR, about 300 minutes a week.",
                "- Goal " + goal + " [ACTIVE, priority 1] \"In-house backend role\" (CAREER), 2026-09-01 to 2027-06-30."
                        + " Why: Ownership of a product Success: A signed offer",
                "  Strategy v1: Portfolio first, then apply Effort: TECHNICAL 70%, DOMAIN 20%, OTHER 10%.",
                "  Phase 1 (key " + phase + ") \"Build the foundation\" 2026-09-01 to 2026-11-30, ACTIVE, exit: "
                        + "Gateway and Strategy released",
                "  Knowledge: ASSUMPTION \"Recruiters only read LinkedIn\" (FALSIFIED)",
                "Week 2026-W46 (active): capacity 270 min, planned 240 min, done 100 min; actions done 1, deferred 1,"
                        + " dropped 0.",
                "Review of 2026-W45: energy 3/5. Went well: Shipped the decisions module Got stuck: Compose took longer"
                        + " Lesson: Estimate infra work double",
                "Feedback (MANAGER, reliability HIGH, 2026-10-15): \"Practise system design\"",
                "Evidence (OUTPUT, PERSONAL_PROJECT, 2026-11-08): \"lifeos-contracts 0.6.0 released\"",
                "Open decision \"Where to get K8s experience\": chose \"Own k3s server\"",
                "Active idea: \"A Life OS CLI\""}) {
            assertTrue(described.contains(expected), "missing <" + expected + "> in\n" + described);
        }
        assertFalse(described.contains("An old decision"), described);
        assertFalse(described.contains("Learn Go"), described);
        assertEquals("tr-TR", contextService.languageOf(user));
    }

    @Test
    void anOlderVersionNeverReplacesANewerOne() {
        UUID user = UUID.randomUUID();
        UUID goal = UUID.randomUUID();
        strategyEvent(user, "GoalChanged", payloads.goal(goal, "Renamed goal", 3));
        strategyEvent(user, "GoalChanged", payloads.goal(goal, "Original goal", 2));

        assertTrue(contextService.describe(user).contains("\"Renamed goal\""));
        assertEquals("tr-TR", contextService.languageOf(user));
        strategyEvent(user, "UserProfileUpdated", payloads.profile("Onurcan", "en-US"));
        assertEquals("en-US", contextService.languageOf(user));
    }

    @Test
    void strategyReportsTheDecisionOnAProposal() {
        UUID user = UUID.randomUUID();
        UUID goal = UUID.randomUUID();
        Recommendation recommendation = recommendationRepository.save(Recommendation.propose(user, goal,
                RecommendationReason.FEEDBACK, 1, "Interviews ask for system design", "{}", clock.instant()));

        strategyEvent(user, "ProposalDecided",
                payloads.proposalDecided(recommendation.getId(), goal, "REJECTED", "I learn it at work"));
        // A second decision (e.g. a redelivery under a new event ID) does not overwrite the first.
        strategyEvent(user, "ProposalDecided", payloads.proposalDecided(recommendation.getId(), goal, "ACCEPTED", null));
        strategyEvent(user, "ProposalDecided", payloads.proposalDecided(UUID.randomUUID(), goal, "ACCEPTED", null));

        assertEquals(Map.of("status", "REJECTED", "decision_note", "I learn it at work"), jdbcClient.sql("""
                        SELECT status, decision_note FROM recommendation WHERE id = :id
                        """)
                .param("id", recommendation.getId()).query().singleRow());
    }

    @Test
    void aDeletedUserLeavesNothingBehind() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (UUID someone : new UUID[] {user, other}) {
            strategyEvent(someone, "GoalChanged", payloads.goal(UUID.randomUUID(), "A goal", 0));
            recommendationRepository.save(Recommendation.propose(someone, UUID.randomUUID(),
                    RecommendationReason.DRIFT, null, "Behind", "{}", clock.instant()));
            jdbcClient.sql("""
                            WITH c AS (INSERT INTO conversation (id, user_id, title, created_at, updated_at)
                                       VALUES (gen_random_uuid(), :u, 'Chat', now(), now()) RETURNING id)
                            INSERT INTO message (id, conversation_id, user_id, role, content, created_at)
                            SELECT gen_random_uuid(), c.id, :u, 'USER', 'Hi', now() FROM c
                            """)
                    .param("u", someone).update();
        }

        strategyEvent(user, "UserDeletionRequested", payloads.userDeletionRequested());
        strategyEvent(user, "UserDeletionRequested", payloads.userDeletionRequested());

        assertEquals(Map.of("context_fact", 0L, "recommendation", 0L, "conversation", 0L, "message", 0L), rows(user));
        assertEquals(Map.of("context_fact", 1L, "recommendation", 1L, "conversation", 1L, "message", 1L),
                rows(other));
    }

    private Map<String, Long> rows(UUID user) {
        return Map.of(
                "context_fact", count("context_fact", user),
                "recommendation", count("recommendation", user),
                "conversation", count("conversation", user),
                "message", count("message", user));
    }

    private long count(String table, UUID user) {
        return jdbcClient.sql("SELECT count(*) FROM " + table + " WHERE user_id = :u").param("u", user)
                .query(Long.class).single();
    }
}
