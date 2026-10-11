package com.example.marketplace.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One question posted from the chat page. */
public class ChatQuestionForm {

    /** Bound as a bean, so it needs getters and setters for Thymeleaf's th:field. */
    @NotBlank(message = "Type a question first.")
    @Size(max = 500, message = "Keep your question under 500 characters.")
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}