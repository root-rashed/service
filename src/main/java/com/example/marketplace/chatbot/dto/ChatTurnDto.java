package com.example.marketplace.chatbot.dto;

import lombok.Value;

/**
 * One line of the visible conversation.
 *
 * <p>A class rather than a record so the template reads its properties through ordinary getters,
 * matching every other DTO in the project.
 */
@Value
public class ChatTurnDto {

    /** True for the visitor's own message, false for the reply. */
    boolean fromCustomer;

    /** The message text. */
    String text;

    /**
     * The structured reply, present only on assistant lines so the page can render the service
     * cards that came with it.
     */
    ChatbotReply reply;

    public static ChatTurnDto question(String text) {
        return new ChatTurnDto(true, text, null);
    }

    public static ChatTurnDto answer(ChatbotReply reply) {
        return new ChatTurnDto(false, reply.getAnswer(), reply);
    }
}