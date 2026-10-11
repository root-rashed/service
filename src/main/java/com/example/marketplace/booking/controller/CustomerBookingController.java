package com.example.marketplace.booking.controller;

import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.dto.BookingRequest;
import com.example.marketplace.booking.service.BookingService;
import com.example.marketplace.common.exception.BookingException;
import com.example.marketplace.common.exception.PaymentException;
import com.example.marketplace.review.service.ReviewService;
import com.example.marketplace.security.AppUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Customer checkout and booking pages. /booking/** and /customer/** require the CUSTOMER role
 * (SecurityConfig). The customer id comes from the authenticated principal, and every booking
 * lookup is scoped to it.
 */
@Controller
public class CustomerBookingController {

    private static final String CHECKOUT_VIEW = "booking/new";

    private final BookingService bookingService;
    private final ReviewService reviewService;

    public CustomerBookingController(BookingService bookingService, ReviewService reviewService) {
        this.bookingService = bookingService;
        this.reviewService = reviewService;
    }

    @GetMapping("/booking/new")
    public String checkout(@RequestParam String service, Model model) {
        BookingRequest form = new BookingRequest();
        form.setServiceSlug(service);
        model.addAttribute("bookingRequest", form);
        model.addAttribute("checkout", bookingService.previewCheckout(service));
        model.addAttribute("startStep", 1);
        return CHECKOUT_VIEW;
    }

    @PostMapping("/booking")
    public String create(@AuthenticationPrincipal AppUserDetails user,
                         @Valid @ModelAttribute("bookingRequest") BookingRequest form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                BookingDto booking = bookingService.create(user.getId(), form);
                redirect.addFlashAttribute("successMessage",
                        "Booking request sent. You'll be notified when the professional responds.");
                return "redirect:/booking/" + booking.getId();
            } catch (BookingException | PaymentException ex) {
                result.reject("booking.failed", ex.getMessage());
            }
        }
        model.addAttribute("checkout", bookingService.previewCheckout(form.getServiceSlug()));
        model.addAttribute("startStep", stepWithProblem(result));
        return CHECKOUT_VIEW;
    }

    @GetMapping("/booking/{id}")
    public String detail(@AuthenticationPrincipal AppUserDetails user, @PathVariable String id, Model model) {
        model.addAttribute("booking", bookingService.getForCustomer(user.getId(), id));
        model.addAttribute("review", reviewService.findForBooking(user.getId(), id));
        return "booking/detail";
    }

    @PostMapping("/booking/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserDetails user,
                         @PathVariable String id,
                         RedirectAttributes redirect) {
        try {
            bookingService.cancelByCustomer(user.getId(), id);
            redirect.addFlashAttribute("successMessage", "Booking cancelled.");
        } catch (BookingException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/booking/" + id;
    }

    @GetMapping("/customer/bookings")
    public String list(@AuthenticationPrincipal AppUserDetails user,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("bookings", bookingService.listForCustomer(user.getId(), page));
        return "customer/bookings";
    }

    /** Which checkout step the page should open on after a failed submit. */
    private int stepWithProblem(BindingResult result) {
        if (result.hasFieldErrors("date") || result.hasFieldErrors("startMinute") || result.hasGlobalErrors()) {
            return 2;
        }
        if (result.hasFieldErrors("address") || result.hasFieldErrors("customerNote")) {
            return 3;
        }
        return 1;
    }
}
