package com.lifeos.coach.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/coach/conversations/{id}/messages}: what the user says; Coach answers as a stream. */
public record SendMessageRequest(@NotBlank @Size(max = 4000) String content) {
}
