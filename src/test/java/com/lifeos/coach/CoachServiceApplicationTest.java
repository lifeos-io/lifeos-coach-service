package com.lifeos.coach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.coach.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;

class CoachServiceApplicationTest extends IntegrationTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void startsWithItsSchemaAndTheFakeModel() throws Exception {
        List<String> tables = jdbcClient.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('context_fact', 'recommendation', 'conversation', 'message')
                        ORDER BY table_name
                        """)
                .query(String.class).list();
        assertEquals(List.of("context_fact", "conversation", "message", "recommendation"), tables);
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        fakeChatModel.respondWith("Merhaba");
        assertEquals("Merhaba", chatClientBuilder.build().prompt().user("Selam").call().content());
    }
}
