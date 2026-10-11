package com.example.marketplace.booking.controller;

import com.example.marketplace.booking.dto.WorkerBookingFilter;
import com.example.marketplace.booking.service.BookingService;
import com.example.marketplace.common.exception.BookingException;
import com.example.marketplace.common.exception.PaymentException;
import com.example.marketplace.payment.service.CashPaymentService;
import com.example.marketplace.security.AppUserDetails;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The worker's booking requests and jobs. Secured by SecurityConfig (/worker/** requires WORKER).
 * The worker id always comes from the authenticated principal.
 */
@Controller
@RequestMapping("/worker/bookings")
public class WorkerBookingController {

    private final BookingService bookingService;
    private final CashPaymentService cashPaymentService;

    public WorkerBookingController(BookingService bookingService, CashPaymentService cashPaymentService) {
        this.bookingService = bookingService;
        this.cashPaymentService = cashPaymentService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails user,
                       @RequestParam(defaultValue = "PENDING") String tab,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        WorkerBookingFilter filter = parse(tab);
        model.addAttribute("bookings", bookingService.listForWorker(user.getId(), filter, page));
        model.addAttribute("counts", bookingService.countsForWorker(user.getId()));
        model.addAttribute("filters", WorkerBookingFilter.values());
        model.addAttribute("currentTab", filter.name());
        return "worker/bookings";
    }

    @PostMapping("/{id}/accept")
    public String accept(@AuthenticationPrincipal AppUserDetails user, @PathVariable String id,
                         @RequestParam(required = false) String tab, RedirectAttributes redirect) {
        return act(redirect, tab, "Booking accepted. The customer has been told.",
                () -> bookingService.accept(user.getId(), id));
    }

    @PostMapping("/{id}/reject")
    public String reject(@AuthenticationPrincipal AppUserDetails user, @PathVariable String id,
                         @RequestParam(required = false) String tab, RedirectAttributes redirect) {
        return act(redirect, tab, "Booking declined.", () -> bookingService.reject(user.getId(), id));
    }

    @PostMapping("/{id}/start")
    public String start(@AuthenticationPrincipal AppUserDetails user, @PathVariable String id,
                        @RequestParam(required = false) String tab, RedirectAttributes redirect) {
        return act(redirect, tab, "Job started.", () -> bookingService.start(user.getId(), id));
    }

    @PostMapping("/{id}/complete")
    public String complete(@AuthenticationPrincipal AppUserDetails user, @PathVariable String id,
                           @RequestParam(required = false) String tab, RedirectAttributes redirect) {
        return act(redirect, tab, "Job marked as completed. Don't forget to confirm the cash payment.",
                () -> bookingService.complete(user.getId(), id));
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserDetails user, @PathVariable String id,
                         @RequestParam(required = false) String tab, RedirectAttributes redirect) {
        return act(redirect, tab, "Booking cancelled.", () -> bookingService.cancelByWorker(user.getId(), id));
    }

    @PostMapping("/{id}/cash-received")
    public String cashReceived(@AuthenticationPrincipal AppUserDetails user, @PathVariable String id,
                               @RequestParam(required = false) String tab, RedirectAttributes redirect) {
        return act(redirect, tab, "Cash payment recorded.", () -> cashPaymentService.markPaidByWorker(user.getId(), id));
    }

    // ---------- Helpers ----------

    private String act(RedirectAttributes redirect, String tab, String successMessage, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("successMessage", successMessage);
        } catch (BookingException | PaymentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        redirect.addAttribute("tab", parse(tab).name());
        return "redirect:/worker/bookings";
    }

    private WorkerBookingFilter parse(String tab) {
        if (tab == null) {
            return WorkerBookingFilter.PENDING;
        }
        try {
            return WorkerBookingFilter.valueOf(tab.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return WorkerBookingFilter.PENDING;
        }
    }
}
