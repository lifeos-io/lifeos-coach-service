package com.lifeos.coach.context.entity;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * One thing Coach knows about the user, as the last event about it described it ({@code data} is the event's
 * payload, or a merge of payloads for a week).
 *
 * @param version the producer's version for full-state events (an older one never replaces a newer one)
 */
public record ContextFact(FactKind kind, UUID subjectId, Integer version, JsonNode data, Instant updatedAt) {
}
