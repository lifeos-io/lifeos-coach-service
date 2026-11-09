package com.lifeos.coach.recommendation.service.impl;

import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.coach.context.dto.GoalStrategy;
import com.lifeos.coach.context.service.ContextService;
import com.lifeos.coach.recommendation.client.RevisionAdvisor;
import com.lifeos.coach.recommendation.dto.Advice;
import com.lifeos.coach.recommendation.entity.Recommendation;
import com.lifeos.coach.recommendation.entity.RecommendationReason;
import com.lifeos.coach.recommendation.entity.RecommendationStatus;
import com.lifeos.coach.recommendation.event.RecommendationProposedV1;
import com.lifeos.coach.recommendation.event.RecommendationProposedV1.RevisionV1;
import com.lifeos.coach.recommendation.event.RecommendationProposedV1.SourceV1;
import com.lifeos.coach.recommendation.repository.RecommendationRepository;
import com.lifeos.coach.recommendation.service.RecommendationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceImpl.class);

    private final RecommendationRepository repository;
    private final ContextService contextService;
    private final RevisionAdvisor revisionAdvisor;
    private final Outbox outbox;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Duration coolDown;

    public RecommendationServiceImpl(RecommendationRepository repository, ContextService contextService,
            RevisionAdvisor revisionAdvisor, Outbox outbox, JsonMapper jsonMapper, Clock clock,
            @Value("${lifeos.coach.recommendation.cool-down:7d}") Duration coolDown) {
        this.repository = repository;
        this.contextService = contextService;
        this.revisionAdvisor = revisionAdvisor;
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.coolDown = coolDown;
    }

    @Override
    @Transactional
    public void consider(UUID userId, RecommendationReason reason, String happened, String sourceType,
            UUID sourceId) {
        List<GoalStrategy> goals = contextService.activeGoalsWithStrategy(userId);
        if (goals.isEmpty()) {
            return;
        }
        GoalStrategy goal = goals.getFirst();
        Instant now = clock.instant();
        if (repository.existsByGoalIdAndStatus(goal.goalId(), RecommendationStatus.PENDING)) {
            log.info("Not proposing for goal {}: a proposal is pending", goal.goalId());
            return;
        }
        boolean coolingDown = repository.findFirstByGoalIdOrderByCreatedAtDesc(goal.goalId())
                .filter(last -> last.getCreatedAt().isAfter(now.minus(coolDown)))
                .isPresent();
        if (coolingDown) {
            log.info("Not proposing for goal {}: last proposal is less than {} old", goal.goalId(), coolDown);
            return;
        }

        Advice advice;
        try {
            advice = revisionAdvisor.advise(contextService.describe(userId), happened, goal,
                    contextService.languageOf(userId));
        } catch (RuntimeException e) {
            log.warn("The model could not advise on goal {} ({}); no proposal this time", goal.goalId(), reason, e);
            return;
        }
        if (advice == null || !advice.change()) {
            log.info("The model sees no reason to revise goal {} after {}", goal.goalId(), reason);
            return;
        }
        List<String> problems = new ArrayList<>();
        RevisionV1 revision = RevisionRules.check(advice, goal, problems);
        if (revision == null) {
            log.warn("The model's revision of goal {} breaks Strategy's rules, not proposed: {}", goal.goalId(),
                    problems);
            return;
        }

        String rationale = advice.rationale().strip();
        Recommendation recommendation = Recommendation.propose(userId, goal.goalId(), reason, goal.versionNo(),
                rationale, jsonMapper.writeValueAsString(revision), now);
        repository.save(recommendation);
        List<SourceV1> sources = sourceType == null ? List.of() : List.of(new SourceV1(sourceType, sourceId));
        outbox.append(new OutboxMessage("Recommendation", recommendation.getId(), userId,
                RecommendationProposedV1.TYPE, RecommendationProposedV1.VERSION,
                new RecommendationProposedV1(recommendation.getId(), RecommendationProposedV1.STRATEGY_REVISION,
                        goal.goalId(), goal.versionNo(), rationale, sources, revision)));
    }

    @Override
    @Transactional
    public void decided(UUID recommendationId, RecommendationStatus decision, String note, Instant decidedAt) {
        repository.findById(recommendationId).ifPresent(recommendation ->
                recommendation.decided(decision, note, decidedAt));
    }
}
