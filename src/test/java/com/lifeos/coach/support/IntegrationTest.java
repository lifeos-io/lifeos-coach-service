package com.lifeos.coach.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.lifeos.base.id.UuidV7;
import com.lifeos.coach.inbound.consumer.StrategyEventConsumer;
import com.lifeos.coach.inbound.consumer.TriggerEventConsumer;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Base of the service's integration tests: the whole application over MockMvc, against real PostgreSQL and
 * Kafka, with {@link FakeChatModel} instead of OpenAI (OpenAI's auto-configuration is off: no key, no network).
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        "spring.ai.model.chat=none",
        // Pings often enough to see one in a short test.
        "lifeos.coach.chat.ping-interval=100ms"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbcClient;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected FakeChatModel fakeChatModel;

    protected static RequestPostProcessor as(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    @Autowired
    private StrategyEventConsumer strategyEventConsumer;

    @Autowired
    private TriggerEventConsumer triggerEventConsumer;

    /** Hands a Strategy event to Coach's consumer, as Kafka would; the payload is checked against its schema. */
    protected UUID strategyEvent(UUID userId, String eventType, JsonNode payload) {
        return event("strategy", userId, eventType, payload);
    }

    /** The same for a Trigger event. */
    protected UUID triggerEvent(UUID userId, String eventType, JsonNode payload) {
        return event("trigger", userId, eventType, payload);
    }

    /** Payloads of the user's Coach events of one type in the outbox, oldest first. */
    protected List<JsonNode> outboxPayloads(UUID userId, String eventType) {
        return jdbcClient.sql("""
                        SELECT payload::text FROM outbox_event
                        WHERE user_id = :userId AND event_type = :eventType ORDER BY seq
                        """)
                .param("userId", userId)
                .param("eventType", eventType)
                .query(String.class)
                .list()
                .stream()
                .map(jsonMapper::readTree)
                .toList();
    }

    private UUID event(String topic, UUID userId, String eventType, JsonNode payload) {
        EventSchemas.assertValidPayload(topic, eventType, 1, payload);
        UUID eventId = UuidV7.next();
        ObjectNode envelope = jsonMapper.createObjectNode()
                .put("eventId", eventId.toString())
                .put("eventType", eventType)
                .put("eventVersion", 1)
                .put("occurredAt", clock.instant().toString())
                .put("userId", userId.toString());
        envelope.set("payload", payload);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("lifeos." + topic + ".events.v1", 0, 0L,
                userId.toString(), jsonMapper.writeValueAsString(envelope));
        if (topic.equals("strategy")) {
            strategyEventConsumer.on(record);
        } else {
            triggerEventConsumer.on(record);
        }
        return eventId;
    }
}
