package com.lifeos.coach.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/coach/conversations}.
 *
 * @param title optional; without one, the first message names the conversation
 */
public record StartConversationRequest(@Size(max = 200) String title) {
}
