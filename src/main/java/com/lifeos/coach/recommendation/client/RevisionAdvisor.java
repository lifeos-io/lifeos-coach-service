package com.lifeos.coach.recommendation.client;

import com.lifeos.coach.context.dto.GoalStrategy;
import com.lifeos.coach.recommendation.dto.Advice;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Asks the model (OpenAI through Spring AI) whether a goal's strategy should change after something happened, and
 * for the complete revision if so. The answer is structured output ({@link Advice}); Coach checks it against
 * Strategy's rules before proposing it.
 */
@Component
public class RevisionAdvisor {

    static final String SYSTEM = """
            You are the coach of Life OS, an app that helps one person reach long-term goals with a strategy \
            (phases and an effort split), weekly plans, evidence and decisions.
            Something just happened in the user's life. Decide whether ONE goal's strategy should change because \
            of it. Propose a change only when the new information really calls for one; otherwise answer \
            change = false. Never invent facts about the user.
            When you propose a revision, give the complete new version:
            - keep a phase you keep with its exact phaseKey; a new phase has phaseKey null;
            - phases lie within the goal's window, startsOn <= endsOn, at most one phase is ACTIVE, status is \
            UPCOMING, ACTIVE or DONE, dates are YYYY-MM-DD;
            - effortAllocation has TECHNICAL, DOMAIN and OTHER, adding up to 100;
            - summary and changeReason are short; rationale tells the user in two or three sentences why.
            Write rationale, summary, changeReason and phase titles in the user's language: %s.""";

    private final ChatClient chatClient;

    public RevisionAdvisor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * @param context  the user's situation ({@code ContextService.describe})
     * @param happened what just happened, in a sentence or two
     * @throws RuntimeException when the model cannot be reached or answers nothing usable
     */
    public Advice advise(String context, String happened, GoalStrategy goal, String language) {
        return chatClient.prompt()
                .system(SYSTEM.formatted(language))
                .user("""
                        The user's situation:
                        %s
                        What just happened: %s

                        Goal to consider: %s "%s" (window %s to %s), current strategy version %d.
                        """.formatted(context, happened, goal.goalId(), goal.title(), goal.windowStart(),
                        goal.windowEnd(), goal.versionNo()))
                .call()
                .entity(Advice.class);
    }
}
