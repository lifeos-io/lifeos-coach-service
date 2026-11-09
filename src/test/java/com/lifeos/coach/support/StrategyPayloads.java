package com.lifeos.coach.support;

import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Complete, schema-valid payloads of the Strategy events Coach consumes (checked against lifeos-contracts). */
public final class StrategyPayloads {

    private final JsonMapper jsonMapper;

    public StrategyPayloads(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public JsonNode profile(String name, String locale) {
        return json("""
                {"displayName": "%s", "timezone": "Europe/Istanbul", "locale": "%s", "defaultWeeklyMinutes": 300,
                 "onboardingCompletedAt": null}
                """.formatted(name, locale));
    }

    public JsonNode goal(UUID goalId, String title, int version) {
        return json("""
                {"goalId": "%s", "title": "%s", "domain": "CAREER", "whyItMatters": "Ownership of a product",
                 "successDefinition": "A signed offer", "windowStart": "2026-09-01", "windowEnd": "2027-06-30",
                 "status": "ACTIVE", "priority": 1, "version": %d}
                """.formatted(goalId, title, version));
    }

    public JsonNode strategy(UUID goalId, int versionNo, UUID phaseKey) {
        return json("""
                {"goalId": "%s", "strategyVersionId": "%s", "versionNo": %d, "previousVersionNo": null,
                 "summary": "Portfolio first, then apply", "changeReason": "Initial strategy", "sourceProposalId": null,
                 "phases": [{"phaseKey": "%s", "ordinal": 1, "title": "Build the foundation", "startsOn": "2026-09-01",
                             "endsOn": "2026-11-30", "exitCriteria": "Gateway and Strategy released", "status": "ACTIVE"}],
                 "effortAllocation": {"TECHNICAL": 70, "DOMAIN": 20, "OTHER": 10}}
                """.formatted(goalId, UUID.randomUUID(), versionNo, phaseKey));
    }

    public JsonNode knowledgeItem(UUID goalId, String statement, String status) {
        return json("""
                {"knowledgeItemId": "%s", "goalId": "%s", "statement": "%s", "kind": "ASSUMPTION", "status": "%s",
                 "external": true, "source": null, "howToVerify": null, "verifiedAt": %s, "version": 1}
                """.formatted(UUID.randomUUID(), goalId, statement, status,
                status.equals("FALSIFIED") ? "\"2026-10-20T10:00:00Z\"" : "null"));
    }

    public JsonNode weekActivated(UUID weeklyPlanId, String isoWeek) {
        return json("""
                {"weeklyPlanId": "%s", "isoWeek": "%s", "weekStart": "2026-11-09", "capacityMinutes": 300,
                 "headline": null, "allocations": [{"goalId": "%s", "strategyVersionId": null, "allocatedMinutes": 240}],
                 "plannedActionCount": 3, "plannedActionMinutes": 240}
                """.formatted(weeklyPlanId, isoWeek, UUID.randomUUID()));
    }

    public JsonNode actionStatusChanged(UUID weeklyPlanId, String isoWeek, int done, int doneMinutes) {
        return json("""
                {"actionId": "%s", "weeklyPlanId": "%s", "isoWeek": "%s", "goalIds": ["%s"], "phaseKey": null,
                 "kind": "TECHNICAL", "estimateMinutes": 60, "previousStatus": "PLANNED", "status": "DONE",
                 "deferredCount": 0, "completedAt": "2026-11-10T10:00:00Z",
                 "week": {"planned": 1, "done": %d, "deferred": 1, "dropped": 0, "plannedMinutes": 240,
                          "doneMinutes": %d}}
                """.formatted(UUID.randomUUID(), weeklyPlanId, isoWeek, UUID.randomUUID(), done, doneMinutes));
    }

    public JsonNode feedback(String content, String reliability) {
        return json("""
                {"feedbackId": "%s", "sourceKind": "MANAGER", "sourceLabel": "Team lead", "content": "%s",
                 "reliability": "%s", "receivedOn": "2026-10-15"}
                """.formatted(UUID.randomUUID(), content, reliability));
    }

    public JsonNode evidence(String title) {
        return json("""
                {"evidenceId": "%s", "title": "%s", "level": "OUTPUT", "strength": "PERSONAL_PROJECT",
                 "occurredOn": "2026-11-08", "parentEvidenceId": null, "actionId": null, "feedbackId": null,
                 "goalIds": [], "phaseKeys": []}
                """.formatted(UUID.randomUUID(), title));
    }

    public JsonNode decisionRecorded(UUID decisionId, String title) {
        return json("""
                {"decisionId": "%s", "goalId": null, "title": "%s", "context": null,
                 "alternatives": [
                   {"label": "A", "description": "Transfer", "chosen": false, "rejectionReason": "Not in my hands"},
                   {"label": "B", "description": "Own k3s server", "chosen": true, "rejectionReason": null}],
                 "expectedOutcome": "K8s on my CV", "decidedAt": "2026-11-01T10:00:00Z", "reviewOn": "2026-12-15",
                 "assumptionIds": [], "reopenTriggers": []}
                """.formatted(decisionId, title));
    }

    public JsonNode decisionClosed(UUID decisionId) {
        return json("""
                {"decisionId": "%s", "goalId": null, "title": "Closed", "expectedOutcome": "x",
                 "outcomeNote": "No interview asked about it", "wouldDecideAgain": "GOOD_CALL_BAD_LUCK",
                 "decidedAt": "2026-11-01T10:00:00Z", "closedAt": "2026-11-09T10:00:00Z"}
                """.formatted(decisionId));
    }

    public JsonNode idea(String title, String status) {
        return json("""
                {"ideaId": "%s", "goalId": null, "title": "%s", "note": null, "status": "%s",
                 "activatedAt": %s, "closedAt": null, "promotedGoalId": null, "latestEvaluation": null,
                 "evaluationCount": 0, "version": 1}
                """.formatted(UUID.randomUUID(), title, status,
                status.equals("ACTIVE") ? "\"2026-11-01T10:00:00Z\"" : "null"));
    }

    public JsonNode weeklyReviewCompleted(UUID weeklyPlanId, String isoWeek) {
        return json("""
                {"weeklyReviewId": "%s", "weeklyPlanId": "%s", "isoWeek": "%s", "weekStart": "2026-11-02",
                 "capacityMinutes": 300,
                 "actions": {"planned": 0, "done": 3, "deferred": 1, "dropped": 0, "plannedMinutes": 200,
                             "doneMinutes": 150},
                 "goals": [], "evidenceCount": 1, "energy": 3, "wentWell": "Shipped the decisions module",
                 "gotStuck": "Compose took longer", "lesson": "Estimate infra work double",
                 "nextWeekFocus": "Ideas module", "completedAt": "2026-11-08T18:00:00Z"}
                """.formatted(UUID.randomUUID(), weeklyPlanId, isoWeek));
    }

    public JsonNode proposalDecided(UUID recommendationId, UUID goalId, String decision, String note) {
        return json("""
                {"proposalId": "%s", "recommendationId": "%s", "goalId": "%s", "decision": "%s", "note": %s,
                 "strategyVersionId": null, "decidedAt": "2026-11-09T12:00:00Z"}
                """.formatted(UUID.randomUUID(), recommendationId, goalId, decision,
                note == null ? "null" : "\"" + note + "\""));
    }

    public JsonNode userDeletionRequested() {
        return json("{\"requestedAt\": \"2026-11-09T10:00:00Z\"}");
    }

    private JsonNode json(String text) {
        return jsonMapper.readTree(text);
    }
}
