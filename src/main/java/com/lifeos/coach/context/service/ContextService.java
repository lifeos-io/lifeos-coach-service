package com.lifeos.coach.context.service;

import com.lifeos.coach.context.dto.GoalStrategy;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * What Coach knows about the user, kept from Strategy's events (never asked from Strategy), and its description
 * for the model's prompts.
 */
public interface ContextService {

    /** Updates what Coach knows from one Strategy event; events Coach does not keep are ignored. */
    void record(UUID userId, String eventType, JsonNode payload);

    /** The user's situation as plain text for a prompt: profile, goals with strategy and knowledge, week, recent history. */
    String describe(UUID userId);

    /** The user's language tag (e.g. {@code tr-TR}), from the profile; Turkish until it is known. */
    String languageOf(UUID userId);

    /** The user's ACTIVE goals that have a strategy, by priority (most important first). */
    List<GoalStrategy> activeGoalsWithStrategy(UUID userId);
}
