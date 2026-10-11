package com.example.marketplace.home.controller;

import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.home.dto.ContactForm;
import com.example.marketplace.home.service.ContactMessageService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Static informational pages: About, FAQ and Contact.
 * All three are public (SecurityConfig PUBLIC_ROUTES) and hold no business logic:
 * the only behaviour is relaying the contact form to {@link ContactMessageService}.
 */
@Controller
public class StaticPageController {

    private final ContactMessageService contactMessageService;

    public StaticPageController(ContactMessageService contactMessageService) {
        this.contactMessageService = contactMessageService;
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("activeNav", "about");
        return "pages/about";
    }

    @GetMapping("/faq")
    public String faq() {
        return "pages/faq";
    }

    @GetMapping("/contact")
    public String contactForm(Model model) {
        if (!model.containsAttribute("contactForm")) {
            model.addAttribute("contactForm", new ContactForm());
        }
        return "pages/contact";
    }

    @PostMapping("/contact")
    public String sendContact(@Valid @ModelAttribute("contactForm") ContactForm form,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("contactForm", form);
            return "pages/contact";
        }
        try {
            contactMessageService.send(form);
            redirect.addFlashAttribute("successMessage",
                    "Thanks for getting in touch. Our team usually replies within one working day.");
        } catch (ValidationException ex) {
            model.addAttribute("contactForm", form);
            if (ex.getFieldErrors().isEmpty()) {
                result.reject("contact.invalid", ex.getMessage());
            } else {
                ex.getFieldErrors().forEach((field, message) -> result.rejectValue(field, "contact.invalid", message));
            }
            return "pages/contact";
        }
        // PRG pattern: never re-render submitted values from the address bar.
        return "redirect:/contact";
    }
}