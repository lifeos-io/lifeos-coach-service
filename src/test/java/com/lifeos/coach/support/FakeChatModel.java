package com.lifeos.coach.support;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Stands in for OpenAI in the tests: answers with what the test set (no network, no cost) and keeps the prompts
 * it received. Streaming sends the answer word by word, optionally slowly.
 */
public class FakeChatModel implements ChatModel {

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile Function<Prompt, String> responder = prompt -> "OK";
    private volatile Duration streamDelay = Duration.ZERO;

    public void respondWith(String answer) {
        this.responder = prompt -> answer;
    }

    public void respondWith(Function<Prompt, String> responder) {
        this.responder = responder;
    }

    public void failWith(RuntimeException failure) {
        this.responder = prompt -> {
            throw failure;
        };
    }

    /** Pause between streamed words (to test keep-alive pings). */
    public void streamDelay(Duration delay) {
        this.streamDelay = delay;
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public void reset() {
        prompts.clear();
        responder = prompt -> "OK";
        streamDelay = Duration.ZERO;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return response(responder.apply(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        prompts.add(prompt);
        String answer;
        try {
            answer = responder.apply(prompt);
        } catch (RuntimeException e) {
            return Flux.error(e);
        }
        List<String> words = Arrays.stream(answer.split("(?<= )")).toList();
        Flux<ChatResponse> chunks = Flux.fromIterable(words).map(FakeChatModel::response);
        return streamDelay.isZero() ? chunks : chunks.delayElements(streamDelay);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
