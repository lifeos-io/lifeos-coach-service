package com.lifeos.coach.conversation.dto;

/** The data of the events of a streamed answer ({@code text/event-stream}), as JSON. */
public final class ChatEvents {

    private ChatEvents() {
    }

    /** {@code event: delta}: the next piece of the answer (append as it is, spaces included). */
    public record Delta(String text) {
    }

    /** {@code event: error}: the answer could not be completed; nothing was stored for it. */
    public record Failure(String code, String detail) {
    }
}
