package com.lifeos.coach.context.entity;

/** The kinds of things Coach knows about a user, each kept from Strategy's events. */
public enum FactKind {
    /** Name, language, weekly time ({@code UserProfileUpdated}); subject = the user. */
    PROFILE,
    /** A goal ({@code GoalChanged}). */
    GOAL,
    /** A goal's current strategy ({@code StrategyRevised}); subject = the goal. */
    STRATEGY,
    /** An entry of a goal's knowledge map ({@code KnowledgeItemChanged}). */
    KNOWLEDGE_ITEM,
    /** A committed week and its latest totals ({@code WeeklyPlanActivated}, {@code ActionStatusChanged}). */
    WEEK,
    /** Evidence of progress ({@code EvidenceRecorded}). */
    EVIDENCE,
    /** Feedback the user received ({@code FeedbackRecorded}). */
    FEEDBACK,
    /** A decision ({@code DecisionRecorded}, {@code DecisionClosed}). */
    DECISION,
    /** An idea ({@code IdeaChanged}). */
    IDEA,
    /** A week's review ({@code WeeklyReviewCompleted}). */
    WEEKLY_REVIEW
}
