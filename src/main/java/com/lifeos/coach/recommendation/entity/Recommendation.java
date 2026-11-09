package com.lifeos.coach.recommendation.entity;

import com.lifeos.base.id.UuidV7;
import com.lifeos.coach.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * A strategy revision Coach proposed for a goal ({@code RecommendationProposed}; its ID is the event's
 * {@code recommendationId}), and the user's decision once Strategy reports it ({@code ProposalDecided}).
 */
@Entity
@Table(name = "recommendation")
public class Recommendation extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;
    private UUID goalId;

    @Enumerated(EnumType.STRING)
    private RecommendationReason reason;

    private Integer basedOnVersionNo;
    private String rationale;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String revision;

    @Enumerated(EnumType.STRING)
    private RecommendationStatus status;

    private String decisionNote;
    private Instant decidedAt;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    protected Recommendation() {
    }

    /** {@code revision} is the proposed version as JSON (the event's {@code revision}). */
    public static Recommendation propose(UUID userId, UUID goalId, RecommendationReason reason,
            Integer basedOnVersionNo, String rationale, String revision, Instant now) {
        Recommendation recommendation = new Recommendation();
        recommendation.id = UuidV7.next();
        recommendation.userId = Objects.requireNonNull(userId);
        recommendation.goalId = Objects.requireNonNull(goalId);
        recommendation.reason = Objects.requireNonNull(reason);
        recommendation.basedOnVersionNo = basedOnVersionNo;
        recommendation.rationale = Objects.requireNonNull(rationale);
        recommendation.revision = Objects.requireNonNull(revision);
        recommendation.status = RecommendationStatus.PENDING;
        recommendation.createdAt = now;
        recommendation.updatedAt = now;
        return recommendation;
    }

    /** The user's decision (or a newer proposal replacing this one), from Strategy. A decided one stays decided. */
    public boolean decided(RecommendationStatus decision, String note, Instant decidedAt) {
        if (status != RecommendationStatus.PENDING || decision == RecommendationStatus.PENDING) {
            return false;
        }
        this.status = decision;
        this.decisionNote = note;
        this.decidedAt = Objects.requireNonNull(decidedAt);
        this.updatedAt = decidedAt;
        return true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public RecommendationReason getReason() {
        return reason;
    }

    public Integer getBasedOnVersionNo() {
        return basedOnVersionNo;
    }

    public String getRationale() {
        return rationale;
    }

    public String getRevision() {
        return revision;
    }

    public RecommendationStatus getStatus() {
        return status;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
