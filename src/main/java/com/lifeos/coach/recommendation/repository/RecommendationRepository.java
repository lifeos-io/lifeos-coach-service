package com.lifeos.coach.recommendation.repository;

import com.lifeos.coach.recommendation.entity.Recommendation;
import com.lifeos.coach.recommendation.entity.RecommendationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    boolean existsByGoalIdAndStatus(UUID goalId, RecommendationStatus status);

    /** The goal's most recent proposal, for the cool-down between proposals. */
    Optional<Recommendation> findFirstByGoalIdOrderByCreatedAtDesc(UUID goalId);

    @Modifying
    @Query("DELETE FROM Recommendation r WHERE r.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
