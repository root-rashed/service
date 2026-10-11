package com.example.marketplace.chatbot.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.marketplace.chatbot.config.ChatbotProperties;
import com.example.marketplace.chatbot.dto.ChatbotReply;
import com.example.marketplace.chatbot.service.ChatbotService;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.security.AppAccessDeniedHandler;
import com.example.marketplace.security.AuthFailureHandler;
import com.example.marketplace.security.SessionPrincipalRefresher;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.user.repository.UserRepository;
import com.example.marketplace.user.service.CustomerDashboardService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renders the chat page, for a visitor with a conversation and for one who has just arrived.
 *
 * <p>The empty branch matters as much as the full one: the page has to work before anyone has
 * asked anything, and the suggested questions come from configuration rather than from a database.
 */
@WebMvcTest(ChatbotController.class)
@Import(SecurityConfig.class)
class ChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatbotProperties properties;

    @MockitoBean
    private ChatbotService chatbotService;

    /** Pulled in by the shared layout fragments; unused by the chat routes. */
    @MockitoBean
    private SessionPrincipalRefresher principalRefresher;

    @MockitoBean
    private CustomerDashboardService customerDashboardService;

    /** Needed to build the application's real filter chain; the chat routes never read it. */
    @MockitoBean
    private UserRepository userRepository;

    /**
     * The three things a sliced web test is missing: the caching the application enables, the
     * configuration properties the controller reads its suggestions and history cap from, and the
     * two entry point handlers the real security chain is wired with.
     *
     * <p>Importing the application's own {@code SecurityConfig} rather than switching the filters
     * off is the point of doing it this way: it is what proves the chat page is reachable by someone
     * who has not signed in, without repeating the route list in a test.
     */
    @TestConfiguration
    static class SlicedBeans {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        ChatbotProperties chatbotProperties() {
            return new ChatbotProperties();
        }

        @Bean
        AuthFailureHandler authFailureHandler(UserRepository users) {
            return new AuthFailureHandler(users);
        }

        @Bean
        AppAccessDeniedHandler appAccessDeniedHandler() {
            return new AppAccessDeniedHandler();
        }
    }

    @Test
    @DisplayName("shows the prompt and the suggestions before anyone has asked anything")
    void rendersEmptyChat() throws Exception {
        mockMvc.perform(get("/chat"))
                .andExpect(status().isOk())
                .andExpect(view().name("chatbot/index"))
                .andExpect(content().string(containsString("Start the conversation")))
                .andExpect(content().string(containsString("Do you take online payment?")));
    }

    @Test
    @DisplayName("shows the answer, the listing it came from and where the answer came from")
    void rendersAnswerWithListing() throws Exception {
        given(chatbotService.ask("how much is a math teacher?")).willReturn(
                new ChatbotReply("Professional Math Teacher (BDT 1000).", List.of(teacher()), true, "rules"));

        mockMvc.perform(post("/chat").with(csrf())
                        .param("question", "how much is a math teacher?"))
                .andExpect(status().isOk())
                .andExpect(view().name("chatbot/index"))
                .andExpect(content().string(containsString("Professional Math Teacher (BDT 1000)")))
                // The card price is formatted with the thousand separator, as every other card is.
                .andExpect(content().string(containsString("&#2547;")))
                .andExpect(content().string(containsString(">1,000<")))
                .andExpect(content().string(containsString("Standard answer")));
    }

    @Test
    @DisplayName("does not claim the rules produced an answer when the model did")
    void modelAnswerIsNotLabelledAsARule() throws Exception {
        given(chatbotService.ask("who are you?"))
                .willReturn(new ChatbotReply("The booking assistant for ServiZone.", List.of(), false, "assistant"));

        mockMvc.perform(post("/chat").with(csrf()).param("question", "who are you?"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Standard answer"))));
    }

    @Test
    @DisplayName("keeps the conversation when a question is rejected")
    void keepsHistoryWhenValidationFails() throws Exception {
        given(chatbotService.ask("hello"))
                .willReturn(ChatbotReply.rules("Hello, and welcome to ServiZone."));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/chat").session(session).with(csrf()).param("question", "hello"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/chat").session(session).with(csrf()).param("question", "   "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("chatForm", "question"))
                // The earlier exchange survives, so a blank box does not throw the page away.
                .andExpect(content().string(containsString("Hello, and welcome to ServiZone.")));
    }

    @Test
    @DisplayName("the suggestion chips come from configuration")
    void suggestionsAreConfigurable() throws Exception {
        mockMvc.perform(get("/chat"))
                .andExpect(model().attribute("suggestions", properties.getSuggestions()));
    }

    private ServiceListingDto teacher() {
        return new ServiceListingDto(
                "service-1", "professional-math-teacher", "worker-1", "category-1", "Tutoring & Education",
                "Professional Math Teacher", "Exam preparation.", List.of("Past papers"),
                new BigDecimal("1000"), null, "per hour", 60, "Niketon", 4, null, null,
                ServiceStatus.ACTIVE, 4.9, 21, "Top rated", "blue", "bi-mortarboard", "orange",
                List.of(), null);
    }
}