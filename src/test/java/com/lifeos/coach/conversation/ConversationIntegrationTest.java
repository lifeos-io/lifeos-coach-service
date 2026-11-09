package com.lifeos.coach.conversation;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.coach.support.IntegrationTest;
import com.lifeos.coach.support.StrategyPayloads;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Chatting with Coach: the streamed answer, keep-alive pings, a failing model, history and access. */
class ConversationIntegrationTest extends IntegrationTest {

    private UUID user;

    @BeforeEach
    void setUp() {
        fakeChatModel.reset();
        clock.set(Instant.parse("2026-11-09T10:00:00Z"));
        user = UUID.randomUUID();
        StrategyPayloads payloads = new StrategyPayloads(jsonMapper);
        strategyEvent(user, "UserProfileUpdated", payloads.profile("Onurcan", "tr-TR"));
        strategyEvent(user, "GoalChanged", payloads.goal(UUID.randomUUID(), "In-house backend role", 0));
    }

    @Test
    void theAnswerStreamsAndIsStored() throws Exception {
        String conversation = start(null);
        fakeChatModel.respondWith("Önce mülakat pratiğine bir saat ayır.");

        String stream = send(conversation, "Bu hafta neye odaklanayım?");

        assertTrue(stream.contains("event:delta"), stream);
        assertTrue(stream.contains("{\"text\":\"Önce \"}"), stream);
        assertTrue(stream.contains("event:done"), stream);
        mockMvc.perform(get("/api/coach/conversations/" + conversation + "/messages").with(as(user)))
                .andExpect(jsonPath("$[*].role").value(contains("USER", "ASSISTANT")))
                .andExpect(jsonPath("$[1].content").value("Önce mülakat pratiğine bir saat ayır."));
        mockMvc.perform(get("/api/coach/conversations").with(as(user)))
                .andExpect(jsonPath("$[0].title").value("Bu hafta neye odaklanayım?"));

        Prompt prompt = fakeChatModel.prompts().getFirst();
        String system = prompt.getSystemMessage().getText();
        assertTrue(system.contains("Answer in the user's language: tr-TR."), system);
        assertTrue(system.contains("\"In-house backend role\""), system);
        assertEquals("Bu hafta neye odaklanayım?", prompt.getUserMessage().getText());
    }

    @Test
    void pingsKeepASlowAnswerAlive() throws Exception {
        String conversation = start("Slow");
        fakeChatModel.streamDelay(Duration.ofMillis(250));
        fakeChatModel.respondWith("Yavaş ama geliyor.");

        String stream = send(conversation, "Merhaba");

        assertTrue(stream.contains(":ping"), stream);
        assertTrue(stream.indexOf(":ping") < stream.indexOf("event:done"), stream);
    }

    @Test
    void aFailingModelEndsTheStreamWithAnErrorAndStoresNoAnswer() throws Exception {
        String conversation = start("Failing");
        fakeChatModel.failWith(new IllegalStateException("OpenAI is down (test)"));

        String stream = send(conversation, "Merhaba");

        assertTrue(stream.contains("event:error"), stream);
        assertTrue(stream.contains("\"code\":\"COACH_MODEL_UNAVAILABLE\""), stream);
        assertFalse(stream.contains("OpenAI is down"), "internals must not reach the client: " + stream);
        mockMvc.perform(get("/api/coach/conversations/" + conversation + "/messages").with(as(user)))
                .andExpect(jsonPath("$[*].role").value(contains("USER")));
    }

    @Test
    void theModelSeesTheConversationSoFar() throws Exception {
        String conversation = start("Two turns");
        fakeChatModel.respondWith("İlk cevap.");
        send(conversation, "İlk soru");
        fakeChatModel.respondWith("İkinci cevap.");
        send(conversation, "İkinci soru");

        Prompt second = fakeChatModel.prompts().getLast();
        // System, then the first question and answer, then the new question.
        assertEquals(4, second.getInstructions().size(), second.toString());
        assertTrue(second.getContents().contains("İlk soru") && second.getContents().contains("İlk cevap."));
    }

    @Test
    void anotherUsersConversationAndAnEmptyMessageFailBeforeStreaming() throws Exception {
        String conversation = start("Mine");

        mockMvc.perform(post("/api/coach/conversations/" + conversation + "/messages").with(as(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"Hi\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/coach/conversations/" + conversation + "/messages").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/coach/conversations/" + conversation + "/messages").with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/coach/conversations").with(as(UUID.randomUUID())))
                .andExpect(jsonPath("$.length()").value(0));
        assertEquals(0, fakeChatModel.prompts().size());
    }

    private String start(String title) throws Exception {
        ResultActions created = mockMvc.perform(post("/api/coach/conversations").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(title == null ? "{}" : "{\"title\": \"" + title + "\"}"))
                .andExpect(status().isCreated());
        return jsonMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    /** Sends a message and returns the whole event stream once it ended. */
    private String send(String conversation, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/coach/conversations/" + conversation + "/messages")
                        .with(as(user)).accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.createObjectNode().put("content", content).toString()))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(10_000);
        // Server-sent events are always UTF-8 (the media type carries no charset).
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
