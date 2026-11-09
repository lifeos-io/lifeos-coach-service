package com.lifeos.coach.context.service.impl;

import com.lifeos.coach.context.dto.GoalStrategy;
import com.lifeos.coach.context.entity.ContextFact;
import com.lifeos.coach.context.entity.FactKind;
import com.lifeos.coach.context.repository.ContextFactRepository;
import com.lifeos.coach.context.service.ContextService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Keeps each fact as the payload of the last event about it (full-state events replace it, guarded by their
 * version; follow-up events such as a closed decision or a week's new totals are merged into it), and turns the
 * facts into a compact description. Old history (evidence, feedback, reviews) is cut to the most recent entries.
 */
@Service
public class ContextServiceImpl implements ContextService {

    static final String DEFAULT_LANGUAGE = "tr-TR";
    private static final int RECENT = 5;

    private final ContextFactRepository repository;
    private final Clock clock;

    public ContextServiceImpl(ContextFactRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(UUID userId, String eventType, JsonNode payload) {
        Instant now = clock.instant();
        switch (eventType) {
            case "UserProfileUpdated" -> repository.upsert(userId, FactKind.PROFILE, userId, null, payload, now);
            case "GoalChanged" -> repository.upsert(userId, FactKind.GOAL, id(payload, "goalId"),
                    payload.path("version").asInt(), payload, now);
            case "StrategyRevised" -> repository.upsert(userId, FactKind.STRATEGY, id(payload, "goalId"),
                    payload.path("versionNo").asInt(), payload, now);
            case "KnowledgeItemChanged" -> repository.upsert(userId, FactKind.KNOWLEDGE_ITEM,
                    id(payload, "knowledgeItemId"), payload.path("version").asInt(), payload, now);
            case "IdeaChanged" -> repository.upsert(userId, FactKind.IDEA, id(payload, "ideaId"),
                    payload.path("version").asInt(), payload, now);
            case "EvidenceRecorded" -> repository.upsert(userId, FactKind.EVIDENCE, id(payload, "evidenceId"), null,
                    payload, now);
            case "FeedbackRecorded" -> repository.upsert(userId, FactKind.FEEDBACK, id(payload, "feedbackId"), null,
                    payload, now);
            case "DecisionRecorded" -> repository.upsert(userId, FactKind.DECISION, id(payload, "decisionId"), null,
                    ((ObjectNode) payload.deepCopy()).put("status", "OPEN"), now);
            case "DecisionClosed" -> merge(userId, FactKind.DECISION, id(payload, "decisionId"), decision -> decision
                    .put("status", "CLOSED")
                    .put("outcomeNote", payload.path("outcomeNote").asString())
                    .put("wouldDecideAgain", payload.path("wouldDecideAgain").asString()));
            case "DecisionSuperseded" -> merge(userId, FactKind.DECISION, id(payload, "decisionId"),
                    decision -> decision.put("status", "SUPERSEDED"));
            case "WeeklyPlanActivated" -> repository.upsert(userId, FactKind.WEEK, id(payload, "weeklyPlanId"), null,
                    payload, now);
            case "CapacityChanged" -> merge(userId, FactKind.WEEK, id(payload, "weeklyPlanId"),
                    week -> week.put("capacityMinutes", payload.path("capacityMinutes").asInt()));
            case "ActionStatusChanged" -> merge(userId, FactKind.WEEK, id(payload, "weeklyPlanId"),
                    week -> week.set("week", payload.path("week").deepCopy()));
            case "WeeklyReviewCompleted" -> {
                repository.upsert(userId, FactKind.WEEKLY_REVIEW, id(payload, "weeklyReviewId"), null, payload, now);
                merge(userId, FactKind.WEEK, id(payload, "weeklyPlanId"), week -> week.put("reviewed", true));
            }
            default -> {
                // Not part of what Coach keeps.
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String languageOf(UUID userId) {
        return repository.find(userId, FactKind.PROFILE, userId)
                .map(profile -> profile.data().path("locale").asString(DEFAULT_LANGUAGE))
                .orElse(DEFAULT_LANGUAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoalStrategy> activeGoalsWithStrategy(UUID userId) {
        Map<FactKind, List<ContextFact>> facts = repository.findAll(userId).stream()
                .collect(Collectors.groupingBy(ContextFact::kind));
        Map<UUID, ContextFact> strategies = byId(facts, FactKind.STRATEGY);
        return facts.getOrDefault(FactKind.GOAL, List.of()).stream()
                .map(ContextFact::data)
                .filter(goal -> "ACTIVE".equals(goal.path("status").asString()))
                .filter(goal -> strategies.containsKey(id(goal, "goalId")))
                .sorted(Comparator.comparingInt(goal -> goal.path("priority").asInt()))
                .map(goal -> {
                    JsonNode strategy = strategies.get(id(goal, "goalId")).data();
                    return new GoalStrategy(id(goal, "goalId"), goal.path("title").asString(),
                            LocalDate.parse(goal.path("windowStart").asString()),
                            LocalDate.parse(goal.path("windowEnd").asString()), strategy.path("versionNo").asInt(),
                            strategy.path("phases").valueStream()
                                    .map(phase -> UUID.fromString(phase.path("phaseKey").asString()))
                                    .collect(Collectors.toSet()));
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String describe(UUID userId) {
        Map<FactKind, List<ContextFact>> facts = repository.findAll(userId).stream()
                .collect(Collectors.groupingBy(ContextFact::kind));
        StringBuilder text = new StringBuilder();

        facts.getOrDefault(FactKind.PROFILE, List.of()).forEach(profile -> text.append("Profile: ")
                .append(field(profile.data(), "displayName", "unnamed")).append(", language ")
                .append(field(profile.data(), "locale", DEFAULT_LANGUAGE)).append(", about ")
                .append(profile.data().path("defaultWeeklyMinutes").asInt()).append(" minutes a week.\n"));

        Map<UUID, ContextFact> strategies = byId(facts, FactKind.STRATEGY);
        Map<String, List<JsonNode>> knowledge = facts.getOrDefault(FactKind.KNOWLEDGE_ITEM, List.of()).stream()
                .map(ContextFact::data)
                .collect(Collectors.groupingBy(item -> item.path("goalId").asString()));
        List<JsonNode> goals = facts.getOrDefault(FactKind.GOAL, List.of()).stream()
                .map(ContextFact::data)
                .sorted(Comparator.comparingInt(goal -> goal.path("priority").asInt()))
                .toList();
        text.append(goals.isEmpty() ? "No goals yet.\n" : "Goals, by priority:\n");
        for (JsonNode goal : goals) {
            String goalId = goal.path("goalId").asString();
            text.append("- Goal ").append(goalId).append(" [").append(goal.path("status").asString())
                    .append(", priority ").append(goal.path("priority").asInt()).append("] \"")
                    .append(goal.path("title").asString()).append("\" (").append(goal.path("domain").asString())
                    .append("), ").append(goal.path("windowStart").asString()).append(" to ")
                    .append(goal.path("windowEnd").asString()).append(".");
            appendIf(text, " Why: ", goal, "whyItMatters");
            appendIf(text, " Success: ", goal, "successDefinition");
            text.append("\n");
            ContextFact strategy = strategies.get(UUID.fromString(goalId));
            if (strategy != null) {
                describeStrategy(text, strategy.data());
            }
            for (JsonNode item : knowledge.getOrDefault(goalId, List.of())) {
                text.append("  Knowledge: ").append(item.path("kind").asString()).append(" \"")
                        .append(item.path("statement").asString()).append("\" (")
                        .append(item.path("status").asString()).append(")\n");
            }
        }

        recent(facts, FactKind.WEEK, 1).forEach(week -> {
            JsonNode data = week.data();
            JsonNode totals = data.path("week");
            text.append("Week ").append(data.path("isoWeek").asString())
                    .append(data.path("reviewed").asBoolean() ? " (reviewed)" : " (active)").append(": capacity ")
                    .append(data.path("capacityMinutes").asInt()).append(" min");
            if (totals.isObject()) {
                text.append(", planned ").append(totals.path("plannedMinutes").asInt()).append(" min, done ")
                        .append(totals.path("doneMinutes").asInt()).append(" min; actions done ")
                        .append(totals.path("done").asInt()).append(", deferred ").append(totals.path("deferred").asInt())
                        .append(", dropped ").append(totals.path("dropped").asInt());
            } else {
                text.append(", planned ").append(data.path("plannedActionMinutes").asInt()).append(" min");
            }
            text.append(".\n");
        });

        recent(facts, FactKind.WEEKLY_REVIEW, 3).forEach(review -> {
            JsonNode data = review.data();
            text.append("Review of ").append(data.path("isoWeek").asString()).append(": energy ")
                    .append(data.path("energy").asInt()).append("/5.");
            appendIf(text, " Went well: ", data, "wentWell");
            appendIf(text, " Got stuck: ", data, "gotStuck");
            appendIf(text, " Lesson: ", data, "lesson");
            text.append("\n");
        });

        recent(facts, FactKind.FEEDBACK, RECENT).forEach(feedback -> text.append("Feedback (")
                .append(feedback.data().path("sourceKind").asString()).append(", reliability ")
                .append(feedback.data().path("reliability").asString()).append(", ")
                .append(feedback.data().path("receivedOn").asString()).append("): \"")
                .append(feedback.data().path("content").asString()).append("\"\n"));

        recent(facts, FactKind.EVIDENCE, RECENT).forEach(evidence -> text.append("Evidence (")
                .append(evidence.data().path("level").asString()).append(", ")
                .append(evidence.data().path("strength").asString()).append(", ")
                .append(evidence.data().path("occurredOn").asString()).append("): \"")
                .append(evidence.data().path("title").asString()).append("\"\n"));

        facts.getOrDefault(FactKind.DECISION, List.of()).stream()
                .filter(decision -> "OPEN".equals(decision.data().path("status").asString()))
                .forEach(decision -> {
                    JsonNode data = decision.data();
                    String chosen = data.path("alternatives").valueStream()
                            .filter(alternative -> alternative.path("chosen").asBoolean())
                            .map(alternative -> alternative.path("description").asString())
                            .findFirst().orElse("?");
                    text.append("Open decision \"").append(data.path("title").asString()).append("\": chose \"")
                            .append(chosen).append("\", expects \"").append(data.path("expectedOutcome").asString())
                            .append("\", review on ").append(data.path("reviewOn").asString()).append("\n");
                });

        facts.getOrDefault(FactKind.IDEA, List.of()).stream()
                .filter(idea -> "ACTIVE".equals(idea.data().path("status").asString()))
                .forEach(idea -> text.append("Active idea: \"").append(idea.data().path("title").asString())
                        .append("\"\n"));
        return text.toString();
    }

    private static void describeStrategy(StringBuilder text, JsonNode strategy) {
        text.append("  Strategy v").append(strategy.path("versionNo").asInt()).append(": ")
                .append(strategy.path("summary").asString()).append(" Effort: ");
        JsonNode effort = strategy.path("effortAllocation");
        text.append("TECHNICAL ").append(effort.path("TECHNICAL").asInt()).append("%, DOMAIN ")
                .append(effort.path("DOMAIN").asInt()).append("%, OTHER ").append(effort.path("OTHER").asInt())
                .append("%.\n");
        for (JsonNode phase : strategy.path("phases")) {
            text.append("  Phase ").append(phase.path("ordinal").asInt()).append(" (key ")
                    .append(phase.path("phaseKey").asString()).append(") \"").append(phase.path("title").asString())
                    .append("\" ").append(phase.path("startsOn").asString()).append(" to ")
                    .append(phase.path("endsOn").asString()).append(", ").append(phase.path("status").asString());
            appendIf(text, ", exit: ", phase, "exitCriteria");
            text.append("\n");
        }
    }

    /** Merges a follow-up event into an existing fact; a fact Coach never saw is left alone. */
    private void merge(UUID userId, FactKind kind, UUID subjectId, Consumer<ObjectNode> change) {
        repository.find(userId, kind, subjectId).ifPresent(fact -> {
            ObjectNode data = (ObjectNode) fact.data().deepCopy();
            change.accept(data);
            repository.upsert(userId, kind, subjectId, fact.version(), data, clock.instant());
        });
    }

    private static Map<UUID, ContextFact> byId(Map<FactKind, List<ContextFact>> facts, FactKind kind) {
        return facts.getOrDefault(kind, List.of()).stream()
                .collect(Collectors.toMap(ContextFact::subjectId, fact -> fact));
    }

    /** The latest {@code limit} facts of a kind, newest first. */
    private static List<ContextFact> recent(Map<FactKind, List<ContextFact>> facts, FactKind kind, int limit) {
        return facts.getOrDefault(kind, List.of()).stream()
                .sorted(Comparator.comparing(ContextFact::updatedAt).reversed())
                .limit(limit)
                .toList();
    }

    private static void appendIf(StringBuilder text, String label, JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isMissingNode() && !value.isNull() && !value.asString().isBlank()) {
            text.append(label).append(value.asString());
        }
    }

    private static String field(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asString();
    }

    private static UUID id(JsonNode payload, String field) {
        return UUID.fromString(payload.path(field).asString());
    }
}
