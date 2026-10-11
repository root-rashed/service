package com.example.marketplace.review.controller;

import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.review.dto.ReviewForm;
import com.example.marketplace.review.service.ReviewService;
import com.example.marketplace.security.AppUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Submitting a review. /review/** requires the CUSTOMER role (SecurityConfig); ownership is checked in the service. */
@Controller
@RequestMapping("/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{bookingId}")
    public String submit(@AuthenticationPrincipal AppUserDetails user,
                         @PathVariable String bookingId,
                         @Valid @ModelAttribute("reviewForm") ReviewForm form,
                         BindingResult result,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", result.getFieldErrors().get(0).getDefaultMessage());
        } else {
            try {
                reviewService.create(user.getId(), bookingId, form);
                redirect.addFlashAttribute("successMessage", "Thank you! Your review has been published.");
            } catch (ValidationException ex) {
                redirect.addFlashAttribute("errorMessage", ex.getMessage());
            }
        }
        return "redirect:/booking/" + bookingId;
    }
}
