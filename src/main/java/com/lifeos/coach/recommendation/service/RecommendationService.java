package com.lifeos.coach.recommendation.service;

import com.lifeos.coach.recommendation.entity.RecommendationReason;
import com.lifeos.coach.recommendation.entity.RecommendationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Coach's strategy revision proposals: asked from the model when something happens that may call for one, checked
 * against Strategy's rules, then published ({@code RecommendationProposed}) for the user's decision in Strategy.
 */
public interface RecommendationService {

    /**
     * Considers proposing a revision of the user's most important active goal with a strategy. Nothing is proposed
     * while that goal has a pending proposal or had one within the cool-down, when the model sees no reason, or
     * when its revision breaks Strategy's rules; a failing model is logged and skipped (proposals are optional and
     * every call costs).
     *
     * @param happened   what just happened, for the prompt
     * @param sourceType the record behind it as {@code RecommendationProposed.sources[].type}, or null
     */
    void consider(UUID userId, RecommendationReason reason, String happened, String sourceType, UUID sourceId);

    /** Strategy reported the user's decision ({@code ProposalDecided}); an unknown or decided one is left alone. */
    void decided(UUID recommendationId, RecommendationStatus decision, String note, Instant decidedAt);
}
