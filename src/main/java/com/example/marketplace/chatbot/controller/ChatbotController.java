package com.example.marketplace.chatbot.controller;

import com.example.marketplace.chatbot.config.ChatbotProperties;
import com.example.marketplace.chatbot.dto.ChatQuestionForm;
import com.example.marketplace.chatbot.dto.ChatTurnDto;
import com.example.marketplace.chatbot.service.ChatbotService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * The chat page.
 *
 * <p>Open to everyone, signed in or not: someone looking for a plumber should not have to register
 * to ask a question. The conversation lives in the HTTP session, which keeps it out of the database
 * and means it disappears with the session.
 *
 * <p>The controller only decides what is on the page and what stays in the conversation. Every word
 * of the answer is produced by {@code ChatbotService}.
 */
@Controller
@RequestMapping("/chat")
public class ChatbotController {

    /** Session key for the visible conversation. */
    private static final String HISTORY = "chatbot.history";

    private final ChatbotService chatbotService;
    private final ChatbotProperties properties;

    public ChatbotController(ChatbotService chatbotService, ChatbotProperties properties) {
        this.chatbotService = chatbotService;
        this.properties = properties;
    }

    @GetMapping
    public String page(@SessionAttribute(name = HISTORY, required = false) List<ChatTurnDto> history,
                       Model model) {
        model.addAttribute("activeNav", "chat");
        return render(history == null ? List.of() : history, new ChatQuestionForm(), null, model);
    }

    @PostMapping
    public String ask(@Valid @ModelAttribute("chatForm") ChatQuestionForm form,
                      BindingResult result,
                      @SessionAttribute(name = HISTORY, required = false) List<ChatTurnDto> history,
                      HttpSession session,
                      Model model) {
        List<ChatTurnDto> turns = history == null ? new ArrayList<>() : new ArrayList<>(history);
        if (result.hasErrors()) {
            // Keep both what was already said and what the visitor typed, so a validation
            // complaint does not throw the conversation away.
            return render(turns, form, result, model);
        }

        String question = form.getQuestion().strip();
        turns.add(ChatTurnDto.question(question));
        turns.add(ChatTurnDto.answer(chatbotService.ask(question)));

        if (turns.size() > properties.getHistorySize()) {
            turns = new ArrayList<>(turns.subList(turns.size() - properties.getHistorySize(), turns.size()));
        }
        session.setAttribute(HISTORY, turns);
        return render(turns, new ChatQuestionForm(), null, model);
    }

    /** Clears the conversation and starts again. */
    @PostMapping("/clear")
    public String clear(HttpSession session) {
        session.removeAttribute(HISTORY);
        return "redirect:/chat";
    }

    private String render(List<ChatTurnDto> turns, ChatQuestionForm form, BindingResult result, Model model) {
        model.addAttribute("turns", turns);
        model.addAttribute("suggestions", properties.getSuggestions());
        // Always present so th:object works on a first visit as well.
        model.addAttribute("chatForm", form);
        // The message is passed as text rather than read through #fields, because the page is also
        // rendered by the GET handler, where there is no BindingResult to read from.
        model.addAttribute("questionError",
                result != null ? result.getFieldError("question") : null);
        return "chatbot/index";
    }
}