package com.example.marketplace.chatbot.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound from the app.chatbot.* properties in application.yml.
 *
 * <p>The assistant is optional by design. When it is switched off, or the local model cannot be
 * reached, the page still answers from the marketplace data using built-in rules, so there is
 * never a dead chat box.
 */
@Component
@ConfigurationProperties(prefix = "app.chatbot")
@Getter
@Setter
public class ChatbotProperties {

    /** Whether to try the local model at all. Turn off to run entirely on the rule-based answers. */
    private boolean enabled = true;

    /**
     * How long to wait for the local model before giving up and answering from the rules.
     * Kept short: an assistant that hangs is worse than a simple one that replies.
     */
    private int timeoutSeconds = 12;

    /** Maximum turns kept in the session conversation. Older turns are dropped. */
    private int historySize = 12;

    /** Maximum services listed in one answer. */
    private int maxResults = 4;

    /**
     * Failed attempts in a row before the model is left alone.
     *
     * <p>Worth having because a refused connection to a local model is retried with a back-off, so
     * without a limit every question waits out the full timeout before the built-in answers reply.
     * The default is one: a single miss already means the model is not answering, and a plain answer
     * from the rules beats a second slow one. The cooling-off period covers a blip.
     */
    private int failureThreshold = 1;

    /** How long to stop trying the model once it has failed repeatedly. */
    private int cooldownSeconds = 120;

    /** Suggested questions shown as chips under an answer. */
    private List<String> suggestions = List.of(
            "Which services do you offer?",
            "How much does a deep clean cost?",
            "How do I book a professional?",
            "Do you take online payment?");

    /** Replaces {@link #suggestions} when set, so a deployment can localise the wording. */
    public void setSuggestions(List<String> suggestions) {
        if (suggestions != null && !suggestions.isEmpty()) {
            this.suggestions = List.copyOf(suggestions);
        }
    }
}