package com.example.marketplace.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.chatbot.config.ChatbotProperties;
import com.example.marketplace.chatbot.dto.ChatbotReply;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.dto.ServiceSearchCriteria;
import com.example.marketplace.servicecatalog.service.PublicServiceQueryService;
import com.example.marketplace.servicecatalog.service.ServiceSearchService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

/**
 * The answers given when no language model is running.
 *
 * <p>These are the guarantees the whole chatbot rests on, so they are pinned here: payment is cash
 * only, availability is never quoted, a price always comes from a real listing, and an unknown
 * question produces "try another word" rather than a guess.
 */
@ExtendWith(MockitoExtension.class)
class ChatbotRuleServiceTest {

    private static final String TEACHER = "Professional Math Teacher";

    /** The one listing the stubbed catalogue holds. */
    private final ServiceListingDto teacher = new ServiceListingDto(
            "s1", "professional-math-teacher", "w1", "c1", "Tutoring & Education", TEACHER,
            "Exam preparation for secondary students.", List.of("Past papers"),
            new BigDecimal("1000"), null, "per hour", 60, "Niketon", 4, null, null,
            ServiceStatus.ACTIVE, 4.9, 21, "Top rated", "blue", "bi-mortarboard", "orange",
            List.of(), null);

    @Mock
    private ServiceSearchService serviceSearchService;

    @Mock
    private PublicServiceQueryService publicServiceQueryService;

    @Mock
    private CategoryService categoryService;

    /** Every search term the rule service sent, in order, so the tests can check the wording. */
    private final List<String> searchedTerms = new ArrayList<>();

    private ChatbotRuleService rules;

    @BeforeEach
    void setUp() {
        ChatbotProperties properties = new ChatbotProperties();
        properties.setMaxResults(4);

        rules = new ChatbotRuleService(serviceSearchService, publicServiceQueryService, categoryService,
                properties, new BigDecimal("5"), 2, "ServiZone", "help@example.com");
    }

    /**
     * Stands in for the catalogue.
     *
     * <p>It answers only when the whole query is one of the distinctive words, which is what makes
     * the probing in {@code find} observable: a question such as "how much is a math teacher"
     * reaches the catalogue as "math teacher", which matches nothing, and only the per-word retry
     * finds the listing.
     */
    private void stubCatalogue() {
        given(serviceSearchService.search(any(ServiceSearchCriteria.class))).willAnswer(invocation -> {
            ServiceSearchCriteria criteria = invocation.getArgument(0);
            searchedTerms.add(criteria.getQ());
            boolean distinctive = criteria.getQ() != null
                    && List.of("math", "maths", "tutor", "tutors", "tuition").contains(criteria.getQ());
            return new PageImpl<>(distinctive ? List.of(teacher) : List.of());
        });
    }

    private void stubCategories() {
        given(categoryService.getActiveCategories()).willReturn(List.of(
                new CategoryDto("c1", "Tutoring & Education", "tutoring-education",
                        "Tutors for every level", "bi-book", "blue", 1, true)));
    }

    @Test
    @DisplayName("says payment is cash only")
    void paymentIsCashOnly() {
        ChatbotReply reply = rules.answer("do you take card payment?");

        assertThat(reply.getAnswer()).contains("cash only");
        assertThat(reply.isGuidanceOnly()).isTrue();
    }

    @Test
    @DisplayName("points at the service page instead of quoting availability")
    void availabilityIsNeverQuoted() {
        ChatbotReply reply = rules.answer("are you free on Friday at 3pm?");

        assertThat(reply.getAnswer()).contains("will not quote").contains("service");
        assertThat(reply.getAnswer()).doesNotContain("3pm", "Friday at");
    }

    @Test
    @DisplayName("quotes the price of a real listing, not the words in the question")
    void searchReportsTheRealPrice() {
        stubCatalogue();

        ChatbotReply reply = rules.answer("how much is a math teacher?");

        assertThat(reply.getAnswer()).contains(TEACHER).contains("BDT 1000");
        assertThat(reply.getServices()).hasSize(1);
        assertThat(reply.getServices().get(0).getPrice()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("throws away the question wording before searching the catalogue")
    void searchIgnoresQuestionWording() {
        stubCatalogue();

        rules.answer("how much does it cost to have a math teacher?");

        assertThat(searchedTerms).isNotEmpty().allSatisfy(term ->
                assertThat(term).contains("math").doesNotContain("cost", "much", "have"));
    }

    @Test
    @DisplayName("retries one word at a time when the whole question matches nothing")
    void searchRetriesOneWordAtATime() {
        stubCatalogue();

        assertThat(rules.answer("tell me about maths tuition").getServices()).hasSize(1);
        assertThat(searchedTerms).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("says nothing was found rather than inventing a match")
    void unknownQuestionIsNotGuessed() {
        stubCatalogue();
        stubCategories();

        ChatbotReply reply = rules.answer("do you repair submarines");

        assertThat(reply.getAnswer()).contains("could not find");
        assertThat(reply.getServices()).isEmpty();
    }

    @Test
    @DisplayName("never suggests the word the visitor already used")
    void suggestionIsNotTheWordAlreadyTried() {
        stubCatalogue();
        stubCategories();

        assertThat(rules.answer("cleaning").getAnswer())
                .contains("could not find")
                .doesNotContain("such as \"cleaning\"")
                .contains("such as \"tutor\"");
    }

    @Test
    @DisplayName("names the platform fee from configuration")
    void feeAnswerUsesConfiguredRate() {
        assertThat(rules.answer("what is the platform fee?").getAnswer()).contains("5%");
    }

    @Test
    @DisplayName("answers the cancellation window from configuration")
    void cancellationAnswerUsesConfiguredWindow() {
        assertThat(rules.answer("can I cancel my booking").getAnswer()).contains("2 hours");
    }

    @Test
    @DisplayName("every reply is marked as coming from the built-in answers")
    void repliesAreLabelledAsRules() {
        ChatbotReply reply = rules.answer("hello");

        assertThat(reply.isFromRules()).isTrue();
        assertThat(reply.getSource()).isEqualTo("rules");
    }

    @Test
    @DisplayName("never leaks a raw user id into an answer")
    void answersDoNotLeakIds() {
        stubCatalogue();

        assertThat(rules.answer("who is the teacher?").getAnswer()).doesNotContain("w1");
    }

    @Test
    @DisplayName("an empty question is answered rather than throwing")
    void emptyQuestionIsHandled() {
        stubCategories();

        ChatbotReply reply = rules.answer("   ");

        assertThat(reply.getAnswer()).isNotBlank();
    }

    @Test
    @DisplayName("search terms are cleaned of the punctuation a visitor types")
    void searchTermsAreCleaned() {
        stubCatalogue();

        rules.answer("Do you have a tutor?!");

        assertThat(searchedTerms).isNotEmpty().allSatisfy(term -> assertThat(term).doesNotContain("?!", "?"));
    }
}