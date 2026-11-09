package com.lifeos.coach.conversation.client;

import com.lifeos.coach.conversation.dto.ChatTurn;
import com.lifeos.coach.conversation.entity.MessageRole;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** Coach's answer in a chat, streamed from the model (OpenAI through Spring AI) piece by piece. */
@Component
public class ChatResponder {

    static final String SYSTEM = """
            You are the coach of Life OS, an app that helps one person reach long-term goals with a strategy, \
            weekly plans, evidence of progress and recorded decisions. Be direct, warm and practical; prefer one \
            concrete next step over general advice. Base what you say about the user only on the situation below \
            and the conversation; if something is not there, ask instead of guessing. You cannot change the plan \
            yourself: when a change seems right, say so and let the user make it in the app.
            Answer in the user's language: %s.

            The user's situation:
            %s""";

    private final ChatClient chatClient;

    public ChatResponder(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /** The answer to the last message of {@code turn.history()}, as it is generated. */
    public Flux<String> answer(ChatTurn turn) {
        List<Message> history = turn.history().stream()
                .map(line -> line.role() == MessageRole.USER
                        ? (Message) new UserMessage(line.content())
                        : new AssistantMessage(line.content()))
                .toList();
        return chatClient.prompt()
                .system(SYSTEM.formatted(turn.language(), turn.context()))
                .messages(history)
                .stream()
                .content();
    }
}
