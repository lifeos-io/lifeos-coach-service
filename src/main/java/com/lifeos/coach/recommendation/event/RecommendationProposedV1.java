package com.lifeos.coach.recommendation.event;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payload of {@code RecommendationProposed} v1 (lifeos-contracts: schemas/coach/RecommendationProposed.v1.schema.json),
 * consumed by Strategy (stores it for the user's decision) and Notification.
 */
public record RecommendationProposedV1(
        UUID recommendationId,
        String kind,
        UUID goalId,
        Integer basedOnVersionNo,
        String rationale,
        List<SourceV1> sources,
        RevisionV1 revision) {

    public static final String TYPE = "RecommendationProposed";
    public static final int VERSION = 1;
    public static final String STRATEGY_REVISION = "STRATEGY_REVISION";

    public record SourceV1(String type, UUID id) {
    }

    public record RevisionV1(String summary, String changeReason, List<PhaseV1> phases,
            Map<String, Integer> effortAllocation) {
    }

    public record PhaseV1(UUID phaseKey, String title, LocalDate startsOn, LocalDate endsOn, String exitCriteria,
            String status) {
    }
}
