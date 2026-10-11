package com.example.marketplace.chatbot.dto;

import java.util.List;

/**
 * The result of one assistant search, handed back to the model as structured data.
 *
 * @param summary  one line stating how many matches there were, used when nothing matches
 * @param matches  the services found, already capped
 * @param note     a sentence the model must repeat rather than fill in from memory
 */
public record AssistantSearchResult(
        String summary,
        List<AssistantServiceView> matches,
        String note) {

    /** The standing reminder attached to every search result. */
    public static final String AVAILABILITY_NOTE =
            "Opening hours and free slots are per professional and change daily, so availability is not "
                    + "listed here. The customer picks a confirmed time slot on the service page.";

    /** A search that found nothing, so the model suggests a different wording rather than guessing. */
    public static AssistantSearchResult none(String summary) {
        return new AssistantSearchResult(summary, List.of(), AVAILABILITY_NOTE);
    }

    public boolean isEmpty() {
        return matches.isEmpty();
    }
}