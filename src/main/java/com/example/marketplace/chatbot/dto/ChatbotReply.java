package com.example.marketplace.chatbot.dto;

import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import java.util.List;
import lombok.Value;

/**
 * One answer shown on the chat page.
 *
 * <p>A class rather than a record so the template reads its properties through ordinary getters,
 * matching every other DTO in the project.
 */
@Value
public class ChatbotReply {

    /** The reply text. */
    String answer;

    /**
     * Services to show as cards under the text. Every one came out of the same search the reply
     * describes, so the cards can never contradict the words.
     */
    List<ServiceListingDto> services;

    /**
     * True when the reply came from the built-in rules because the local model was unavailable, so
     * the page can be honest about where the answer is from.
     */
    boolean fromRules;

    /** Where the answer came from, for display: {@code assistant} or {@code rules}. */
    String source;

    /** True when the reply has no service cards and is pure guidance text. */
    public boolean isGuidanceOnly() {
        return services == null || services.isEmpty();
    }

    /** A guidance-only reply from the built-in rules. */
    public static ChatbotReply rules(String answer) {
        return new ChatbotReply(answer, List.of(), true, "rules");
    }

    /** A guidance-only reply from the model. */
    public static ChatbotReply assistant(String answer) {
        return new ChatbotReply(answer, List.of(), false, "assistant");
    }
}