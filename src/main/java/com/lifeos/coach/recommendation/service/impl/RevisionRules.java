package com.lifeos.coach.recommendation.service.impl;

import com.lifeos.coach.context.dto.GoalStrategy;
import com.lifeos.coach.recommendation.dto.Advice;
import com.lifeos.coach.recommendation.event.RecommendationProposedV1.PhaseV1;
import com.lifeos.coach.recommendation.event.RecommendationProposedV1.RevisionV1;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Strategy's rules for a strategy version, checked on the model's revision before it is proposed: a proposal the
 * user could not accept is never sent. (Strategy checks them again on accept, against the state at that time.)
 */
final class RevisionRules {

    private static final Set<String> CATEGORIES = Set.of("TECHNICAL", "DOMAIN", "OTHER");
    private static final Set<String> STATUSES = Set.of("UPCOMING", "ACTIVE", "DONE");

    private RevisionRules() {
    }

    /** The revision as the event carries it, or {@code null} with {@code problems} filled. */
    static RevisionV1 check(Advice advice, GoalStrategy goal, List<String> problems) {
        Advice.Revision revision = advice.revision();
        text(problems, "rationale", advice.rationale(), 2000);
        if (revision == null) {
            problems.add("revision is missing");
            return null;
        }
        text(problems, "summary", revision.summary(), 2000);
        text(problems, "changeReason", revision.changeReason(), 2000);

        Map<String, Integer> effort = new LinkedHashMap<>();
        Map<String, Integer> given = revision.effortAllocation() == null ? Map.of() : revision.effortAllocation();
        for (String category : List.of("TECHNICAL", "DOMAIN", "OTHER")) {
            Integer value = given.get(category);
            int percent = value == null ? 0 : value;
            if (percent < 0 || percent > 100) {
                problems.add("effortAllocation." + category + " is outside 0-100");
            }
            effort.put(category, percent);
        }
        if (!CATEGORIES.containsAll(given.keySet())) {
            problems.add("effortAllocation has unknown categories " + given.keySet());
        }
        int total = effort.values().stream().mapToInt(Integer::intValue).sum();
        if (total != 100) {
            problems.add("effortAllocation adds up to " + total + ", not 100");
        }

        List<Advice.Phase> phases = revision.phases() == null ? List.of() : revision.phases();
        if (phases.isEmpty() || phases.size() > 12) {
            problems.add("there must be 1 to 12 phases, got " + phases.size());
        }
        List<PhaseV1> checked = new ArrayList<>();
        Set<UUID> seenKeys = new HashSet<>();
        long active = 0;
        for (int i = 0; i < phases.size(); i++) {
            Advice.Phase phase = phases.get(i);
            String at = "phases[" + i + "].";
            text(problems, at + "title", phase.title(), 200);
            if (phase.exitCriteria() != null && phase.exitCriteria().length() > 2000) {
                problems.add(at + "exitCriteria is too long");
            }
            if (!STATUSES.contains(phase.status())) {
                problems.add(at + "status " + phase.status() + " is not UPCOMING, ACTIVE or DONE");
            } else if (phase.status().equals("ACTIVE")) {
                active++;
            }
            UUID key = null;
            if (phase.phaseKey() != null && !phase.phaseKey().isBlank()) {
                try {
                    key = UUID.fromString(phase.phaseKey());
                } catch (IllegalArgumentException e) {
                    problems.add(at + "phaseKey is not a UUID");
                }
                if (key != null && !goal.phaseKeys().contains(key)) {
                    problems.add(at + "phaseKey is not a phase of the current strategy");
                } else if (key != null && !seenKeys.add(key)) {
                    problems.add(at + "phaseKey appears more than once");
                }
            }
            LocalDate startsOn = date(problems, at + "startsOn", phase.startsOn());
            LocalDate endsOn = date(problems, at + "endsOn", phase.endsOn());
            if (startsOn != null && endsOn != null) {
                if (endsOn.isBefore(startsOn)) {
                    problems.add(at + "endsOn is before startsOn");
                }
                if (startsOn.isBefore(goal.windowStart()) || endsOn.isAfter(goal.windowEnd())) {
                    problems.add(at + "lies outside the goal's window");
                }
            }
            checked.add(new PhaseV1(key, phase.title(), startsOn, endsOn, phase.exitCriteria(), phase.status()));
        }
        if (active > 1) {
            problems.add("more than one phase is ACTIVE");
        }
        return problems.isEmpty()
                ? new RevisionV1(revision.summary().strip(), revision.changeReason().strip(), checked, effort)
                : null;
    }

    private static void text(List<String> problems, String field, String value, int max) {
        if (value == null || value.isBlank()) {
            problems.add(field + " is blank");
        } else if (value.length() > max) {
            problems.add(field + " is longer than " + max);
        }
    }

    private static LocalDate date(List<String> problems, String field, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            problems.add(field + " is not a date");
            return null;
        }
    }
}
