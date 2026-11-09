package com.lifeos.coach.recommendation.entity;

/** What prompted Coach to propose a revision. */
public enum RecommendationReason {
    /** Feedback the user received ({@code FeedbackRecorded}, reliability HIGH or MEDIUM). */
    FEEDBACK,
    /** A week's look-back ({@code WeeklyReviewCompleted}). */
    WEEKLY_REVIEW,
    /** The week falling behind its plan (Trigger's {@code DriftDetected}). */
    DRIFT
}
