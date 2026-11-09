package com.lifeos.coach.conversation.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.coach.conversation.client.ChatResponder;
import com.lifeos.coach.conversation.dto.ChatEvents;
import com.lifeos.coach.conversation.dto.ChatTurn;
import com.lifeos.coach.conversation.dto.ConversationResponse;
import com.lifeos.coach.conversation.dto.MessageResponse;
import com.lifeos.coach.conversation.dto.SendMessageRequest;
import com.lifeos.coach.conversation.dto.StartConversationRequest;
import com.lifeos.coach.conversation.service.ConversationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * {@code /api/coach/conversations}: chats with Coach. An answer streams as server-sent events: {@code delta}
 * pieces, then {@code done} with the stored message, or {@code error}; a {@code :ping} comment keeps the stream
 * alive while the model thinks (the gateway closes a stream silent for 60 s).
 */
@RestController
@RequestMapping("/api/coach/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversationService;
    private final ChatResponder chatResponder;
    private final Duration pingInterval;

    public ConversationController(ConversationService conversationService, ChatResponder chatResponder,
            @Value("${lifeos.coach.chat.ping-interval:15s}") Duration pingInterval) {
        this.conversationService = conversationService;
        this.chatResponder = chatResponder;
        this.pingInterval = pingInterval;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> start(@CurrentUser UUID userId,
            @Valid @RequestBody(required = false) StartConversationRequest request) {
        ConversationResponse conversation = conversationService.start(userId, request == null ? null : request.title());
        return ResponseEntity.created(URI.create("/api/coach/conversations/" + conversation.id())).body(conversation);
    }

    /** Most recently active first. */
    @GetMapping
    public List<ConversationResponse> list(@CurrentUser UUID userId) {
        return conversationService.list(userId);
    }

    @GetMapping("/{conversationId}/messages")
    public List<MessageResponse> messages(@CurrentUser UUID userId, @PathVariable UUID conversationId) {
        return conversationService.messages(userId, conversationId);
    }

    /**
     * Stores the message and streams Coach's answer. A missing conversation or an invalid message fails before the
     * stream starts (a normal problem response); once streaming, a failing model ends it with {@code error}.
     */
    @PostMapping(path = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> send(@CurrentUser UUID userId, @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        ChatTurn turn = conversationService.begin(userId, conversationId, request.content());
        StringBuilder answer = new StringBuilder();

        Flux<ServerSentEvent<?>> pieces = Flux.defer(() -> chatResponder.answer(turn))
                .filter(piece -> !piece.isEmpty())
                .doOnNext(answer::append)
                .map(piece -> ServerSentEvent.builder(new ChatEvents.Delta(piece)).event("delta").build());
        Flux<ServerSentEvent<?>> done = Mono.fromCallable(() -> {
                    if (answer.toString().isBlank()) {
                        throw new IllegalStateException("The model returned an empty answer");
                    }
                    return conversationService.finish(userId, conversationId, answer.toString());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .<ServerSentEvent<?>>map(message -> ServerSentEvent.builder(message).event("done").build())
                .flux();
        Flux<ServerSentEvent<?>> reply = pieces.concatWith(done)
                .onErrorResume(e -> {
                    log.warn("Coach could not answer in conversation {}", conversationId, e);
                    return Mono.just(ServerSentEvent.builder(new ChatEvents.Failure("COACH_MODEL_UNAVAILABLE",
                            "Coach could not answer right now. Try again in a moment.")).event("error").build());
                });
        Flux<ServerSentEvent<?>> pings = Flux.interval(pingInterval, pingInterval)
                .map(tick -> ServerSentEvent.builder().comment("ping").build());

        return Flux.merge(reply, pings)
                .takeUntil(event -> "done".equals(event.event()) || "error".equals(event.event()));
    }
}
