package com.lifeos.coach.recommendation.dto;

import java.util.List;
import java.util.Map;

/**
 * The model's answer to "should this goal's strategy change?", read from its structured output. Dates are kept
 * as text and checked by Coach's rules before anything is proposed.
 *
 * @param change    whether a revision is worth proposing
 * @param rationale why, for the user, in their language
 * @param revision  the complete proposed version (when {@code change})
 */
public record Advice(boolean change, String rationale, Revision revision) {

    /** A complete strategy version in the shape of {@code RecommendationProposed.revision}. */
    public record Revision(String summary, String changeReason, List<Phase> phases,
            Map<String, Integer> effortAllocation) {
    }

    /** {@code phaseKey}: a phase of the current version carried over, or null for a new phase. */
    public record Phase(String phaseKey, String title, String startsOn, String endsOn, String exitCriteria,
            String status) {
    }
}
