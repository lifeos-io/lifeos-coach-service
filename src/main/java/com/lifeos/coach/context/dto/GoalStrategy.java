package com.lifeos.coach.context.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * An active goal with its current strategy, as far as a revision proposal needs it.
 *
 * @param versionNo the current strategy version (the proposal is based on it)
 * @param phaseKeys keys of the current version's phases (a proposed phase may carry one of them over)
 */
public record GoalStrategy(UUID goalId, String title, LocalDate windowStart, LocalDate windowEnd, int versionNo,
        Set<UUID> phaseKeys) {
}
