package com.lifeos.coach.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Checks events against the JSON Schemas shipped in the lifeos-contracts jar ({@code schemas/...}). */
public final class EventSchemas {

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private EventSchemas() {
    }

    /** The envelope as it goes to Kafka. */
    public static void assertValidEnvelope(JsonNode envelope) {
        assertValid("schemas/envelope.schema.json", envelope);
    }

    /** A consumed event payload, e.g. {@code ("trigger", "DecisionReviewDue", 1, payload)}. */
    public static void assertValidPayload(String topic, String eventType, int version, JsonNode payload) {
        assertValid("schemas/" + topic + "/" + eventType + ".v" + version + ".schema.json", payload);
    }

    private static void assertValid(String resource, JsonNode json) {
        List<Error> errors = load(resource).validate(json);
        assertEquals(List.of(), errors.stream().map(Error::toString).toList(), resource + " rejects " + json);
    }

    private static Schema load(String resource) {
        try (InputStream in = EventSchemas.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "missing in lifeos-contracts: " + resource);
            return REGISTRY.getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
