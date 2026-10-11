package com.example.marketplace.chatbot.service;

import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.chatbot.config.ChatbotProperties;
import com.example.marketplace.chatbot.dto.ChatbotReply;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.dto.ServiceSearchCriteria;
import com.example.marketplace.servicecatalog.dto.ServiceSort;
import com.example.marketplace.servicecatalog.service.PublicServiceQueryService;
import com.example.marketplace.servicecatalog.service.ServiceSearchService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The chatbot's answer when the language model is not available.
 *
 * <p>Every reply is assembled from live marketplace data and configuration through the same
 * {@code ServiceSearchService} the public catalogue uses. There is no canned text file and no free
 * form generation, so the fallback cannot tell a customer something the site would not.
 *
 * <p>Matching is keyword based on purpose: a visitor typing "do you take card?" must reach the
 * cash-only answer whether or not the model is running, so the fallback answers are the source of
 * truth and the model is allowed to rephrase them, never to replace them.
 */
@Service
public class ChatbotRuleService {

    private static final int POPULAR_LIMIT = 4;

    /** How many individual words to try when searching a whole question finds nothing. */
    private static final int TERM_PROBES = 4;

    /**
     * Ordinary words a visitor might try, used only to suggest a plainer wording when a search
     * comes up empty. Deliberately not tied to the catalogue: a suggestion is not a listing, and
     * offering one that does not exist would be a promise the site cannot keep.
     */
    private static final List<String> EXAMPLE_WORDS = List.of("cleaning", "tutor", "electrician", "repair");

    /**
     * Words dropped before searching: question wording, pronouns, and the words people use to ask
     * about money, since the price is in the listing they are about to be shown.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "any", "are", "around", "at", "be", "can", "charge", "charges", "cost",
            "costs", "did", "do", "does", "for", "from", "have", "how", "i", "in", "is", "it", "me",
            "much", "my", "need", "near", "of", "on", "or", "please", "price", "prices", "rate",
            "rates", "some", "tell", "that", "the", "there", "this", "to", "want", "was", "what",
            "whats", "when", "where", "which", "who", "why", "will", "with", "would", "you", "your");

    private final ServiceSearchService serviceSearchService;
    private final PublicServiceQueryService publicServiceQueryService;
    private final CategoryService categoryService;
    private final ChatbotProperties properties;
    private final BigDecimal platformFeePercent;
    private final int cancelCutoffHours;
    private final String appName;
    private final String contactEmail;

    public ChatbotRuleService(ServiceSearchService serviceSearchService,
                              PublicServiceQueryService publicServiceQueryService,
                              CategoryService categoryService,
                              ChatbotProperties properties,
                              @Value("${app.booking.platform-fee-percent:5}") BigDecimal platformFeePercent,
                              @Value("${app.booking.cancel-cutoff-hours:2}") int cancelCutoffHours,
                              @Value("${app.name:ServiZone}") String appName,
                              @Value("${app.contact.to:}") String contactEmail) {
        this.serviceSearchService = serviceSearchService;
        this.publicServiceQueryService = publicServiceQueryService;
        this.categoryService = categoryService;
        this.properties = properties;
        this.platformFeePercent = platformFeePercent;
        this.cancelCutoffHours = cancelCutoffHours;
        this.appName = appName;
        this.contactEmail = contactEmail;
    }

    /**
     * Answers a question from marketplace data alone.
     *
     * @param question the customer's own words, already trimmed
     * @return a reply built from real data, never an apology
     */
    public ChatbotReply answer(String question) {
        String text = question.toLowerCase(Locale.ROOT);

        if (containsAny(text, "hello", "hi ", "hi!", "hey", "good morning", "good evening",
                "salam", "assalam")) {
            return ChatbotReply.rules("Hello, and welcome to " + appName + ". " + categoryLine()
                    + " What do you need done today?");
        }
        if (containsAny(text, "thank", "thx")) {
            return ChatbotReply.rules("Happy to help. Tell me the kind of job you need and I will list "
                    + "professionals with their prices.");
        }
        if (containsAny(text, "bye", "goodbye", "see you")) {
            return ChatbotReply.rules("Goodbye. Come back whenever you need a professional.");
        }
        if (containsAny(text, "payment", "pay", "card", "cash", "bkash", "nagad", "online", "wallet")) {
            return paymentAnswer();
        }
        if (containsAny(text, "fee", "commission", "charge", "hidden cost", "extra cost")) {
            return ChatbotReply.rules("The price you see is the professional's own. " + appName
                    + " adds a platform fee of " + trimFee(platformFeePercent)
                    + " on top, which covers verification, support and hosting. There is no "
                    + "subscription, no listing fee and no surge pricing.");
        }
        if (containsAny(text, "cancel", "refund", "reschedule")) {
            return ChatbotReply.rules("You can cancel free of charge up to " + cancelCutoffHours
                    + " hours before the start time, from the booking page. Because payment is in cash "
                    + "after the job, there is nothing to refund once the work has been done.");
        }
        if (containsAny(text, "available", "availability", "are you free", "am i free", "when can",
                "when are", "free slot", "free time", "free now", "free on", "free this", "open now",
                "open today", "working hours", "schedule", "timing", "slot")) {
            return ChatbotReply.rules("Working hours are set by each professional and their free slots "
                    + "change daily, so I will not quote them here. Open a service and the page shows the "
                    + "confirmed slots; only those can be booked.");
        }
        if (containsAny(text, "trust", "verified", "safe", "background check", "genuine", "scam")) {
            return ChatbotReply.rules("Every professional is reviewed by our team before any service "
                    + "appears publicly, and customers rate them after each job. You can always see the "
                    + "rating, the review count and the number of completed jobs before you book.");
        }
        if (containsAny(text, "contact", "phone", "call", "email", "support", "human", "agent", "complain")) {
            return contactAnswer();
        }
        if (containsAny(text, "category", "categories", "type of service", "types of service",
                "kind of service", "kinds of service", "what do you offer", "what services")) {
            return ChatbotReply.rules("Here is everything on " + appName + ": " + categoryLine()
                    + " Tell me which one you need and I will list what is available.");
        }
        if (containsAny(text, "popular", "trending", "top rated", "best", "recommended")) {
            return popularAnswer();
        }

        // Anything else is treated as something to look for in the catalogue, which is the most
        // common reason someone opens a chat on a marketplace.
        return searchAnswer(question);
    }

    // ---------- Intent answers ----------

    private ChatbotReply paymentAnswer() {
        return ChatbotReply.rules(appName + " is cash only. You agree the work and the price in the chat, "
                + "the professional does the job, and you pay them in person. Nothing is charged "
                + "automatically and we never ask for card or bank details.");
    }

    private ChatbotReply contactAnswer() {
        StringBuilder reply = new StringBuilder(
                "If you need a person, use the contact form and we will reply by email.");
        if (!contactEmail.isBlank()) {
            reply.append(" Messages go to ").append(contactEmail).append('.');
        }
        return ChatbotReply.rules(reply.append(" For anything already booked, the booking page is "
                + "the fastest route: it shows the current status and the professional's contact "
                + "details.").toString());
    }

    /** The highest rated live services, so the reply names real listings instead of a process. */
    private ChatbotReply popularAnswer() {
        List<ServiceListingDto> popular = publicServiceQueryService.getPopular(POPULAR_LIMIT);
        if (popular.isEmpty()) {
            return ChatbotReply.rules("No services are published yet. Once professionals are approved, "
                    + "their listings appear here and on the services page.");
        }
        return withServices("Right now the highest rated services are:", popular);
    }

    /**
     * Reports what the catalogue actually holds for the customer's own words.
     *
     * <p>The same search service backs this and the assistant tools, so the fallback and the model
     * can never disagree about what exists or what it costs. The cards come straight from the
     * search result, so nothing on screen is generated text.
     *
     * <p>The question is reduced to its content words first. Nobody searches a catalogue by typing
     * "how much does a deep clean cost"; the words that identify the service are "deep clean", and
     * the rest is conversation.
     */
    private ChatbotReply searchAnswer(String question) {
        List<String> terms = searchTerms(question);
        List<ServiceListingDto> found = find(terms);
        if (found.isEmpty()) {
            return ChatbotReply.rules("I could not find a published service matching \"" + question
                    + "\". " + categoryLine() + " " + tryAnotherWord(terms)
                    + "Or browse the full list of services.");
        }

        List<ServiceListingDto> matches = found.stream()
                .limit(properties.getMaxResults())
                .toList();

        // The separator is added before each entry but the first, so the list never ends dangling.
        StringBuilder summary = new StringBuilder();
        for (ServiceListingDto service : matches) {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(service.getTitle()).append(" (BDT ")
                    .append(service.getPrice().stripTrailingZeros().toPlainString());
            if (service.getReviewCount() > 0) {
                summary.append(", rated ").append(String.format(Locale.ROOT, "%.1f", service.getRating()));
            }
            summary.append(')');
        }
        summary.append(". Open a service to see what it includes and pick a confirmed time slot.");

        return withServices(summary.toString(), matches);
    }

    /**
     * Suggests wording to try instead.
     *
     * <p>Only a word the visitor has not just used: telling someone who asked for "cleaning" to try
     * "cleaning" wastes their time and reads as though the search never ran.
     */
    private static String tryAnotherWord(List<String> terms) {
        for (String example : EXAMPLE_WORDS) {
            if (!terms.contains(example)) {
                return "Try a plainer word such as \"" + example + "\". ";
            }
        }
        return "Try naming the kind of job more plainly. ";
    }

    /**
     * The listings matching the given search terms, or an empty list.
     *
     * <p>Always a list, never null, so callers have one thing to check.
     */
    private List<ServiceListingDto> find(List<String> terms) {
        if (terms.isEmpty()) {
            return List.of();
        }
        List<ServiceListingDto> matches = search(String.join(" ", terms));
        if (matches.isEmpty() && terms.size() > 1) {
            // A multi word search misses a listing titled with only one of the words, so each word
            // is tried on its own until one hits. Probes are capped so a long question cannot turn
            // into a burst of queries.
            for (String term : terms.stream().limit(TERM_PROBES).toList()) {
                matches = search(term);
                if (!matches.isEmpty()) {
                    break;
                }
            }
        }
        return matches;
    }

    private List<ServiceListingDto> search(String term) {
        ServiceSearchCriteria criteria = new ServiceSearchCriteria();
        criteria.setQ(term);
        criteria.setSort(ServiceSort.RATING);
        criteria.setPage(0);
        return serviceSearchService.search(criteria).getContent();
    }

    /**
     * The content words of a question: the tokens that are not filler.
     *
     * <p>A deliberately short list of common English function and pricing words. It is not a
     * language tool, and it does not need to be: the point is to throw away the wrapper around a
     * question so the words that name a service are left to search with.
     */
    private static List<String> searchTerms(String question) {
        String cleaned = question.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        List<String> terms = new ArrayList<>();
        for (String token : cleaned.split("\\s+")) {
            if (token.isBlank() || STOP_WORDS.contains(token)) {
                continue;
            }
            if (!terms.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    // ---------- Answer building ----------

    private ChatbotReply withServices(String text, List<ServiceListingDto> services) {
        List<ServiceListingDto> cards = new ArrayList<>(services);
        return new ChatbotReply(text, cards, true, "rules");
    }

    /** One sentence naming every live category, read fresh so a new category appears immediately. */
    private String categoryLine() {
        List<CategoryDto> categories = categoryService.getActiveCategories();
        if (categories.isEmpty()) {
            return "No categories are published yet.";
        }
        List<String> names = new ArrayList<>();
        for (CategoryDto category : categories) {
            names.add(category.getName());
        }
        return "We cover " + String.join(", ", names) + ".";
    }

    // ---------- Small helpers ----------

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String trimFee(BigDecimal percent) {
        return percent.stripTrailingZeros().toPlainString() + "%";
    }
}