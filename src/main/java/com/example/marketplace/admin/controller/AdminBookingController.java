package com.example.marketplace.admin.controller;

import com.example.marketplace.admin.service.AdminBookingService;
import com.example.marketplace.admin.web.AdminFilters;
import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.service.BookingService;
import com.example.marketplace.payment.service.CashPaymentService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Booking oversight.
 *
 * Two escape hatches are exposed, and both delegate rather than write documents themselves:
 * {@link BookingService#cancelByAdmin} keeps the state machine, audit trail and time-slot lock
 * release in one place, and {@link CashPaymentService#markPaidByAdmin} keeps the cash-payment
 * rules (completed job, not already settled) in one place. Everything else is read-only.
 */
@Controller
@RequestMapping("/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;
    private final BookingService bookingService;
    private final CashPaymentService cashPaymentService;

    public AdminBookingController(AdminBookingService adminBookingService,
                                  BookingService bookingService,
                                  CashPaymentService cashPaymentService) {
        this.adminBookingService = adminBookingService;
        this.bookingService = bookingService;
        this.cashPaymentService = cashPaymentService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String payment,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<BookingDto> bookings = adminBookingService.search(status, payment, q, page);
        model.addAttribute("bookings", bookings);
        model.addAttribute("counts", adminBookingService.countsByStatus());
        model.addAttribute("statuses", adminBookingService.statuses());
        model.addAttribute("paymentStatuses", adminBookingService.paymentStatuses());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("payment", payment == null ? "" : payment);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("filterQuery", AdminFilters.query("status", status, "payment", payment, "q", q));
        model.addAttribute("activeSection", "bookings");
        return "admin/bookings";
    }

    /** The reconciliation queue: completed jobs whose cash was never confirmed. */
    @GetMapping("/unpaid")
    public String unpaid(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("bookings", adminBookingService.getUnpaid(page));
        model.addAttribute("activeSection", "bookings");
        return "admin/booking-unpaid";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        BookingDto booking = adminBookingService.get(id);
        model.addAttribute("booking", booking);
        model.addAttribute("history", adminBookingService.getHistory(id));
        model.addAttribute("canMarkPaid", adminBookingService.canMarkPaid(booking));
        model.addAttribute("canCancel", adminBookingService.isOpen(booking.getBookingStatus()));
        model.addAttribute("activeSection", "bookings");
        return "admin/booking-detail";
    }

    /** Confirms cash handed over in person. Still a cash-only flow: nothing is charged online. */
    @PostMapping("/{id}/mark-paid")
    public String markPaid(@PathVariable String id, RedirectAttributes flash) {
        cashPaymentService.markPaidByAdmin(id);
        flash.addFlashAttribute("successMessage", "Cash payment confirmed.");
        return "redirect:/admin/bookings/" + id;
    }

    /** Cancels a booking from any state, releasing the professional's time slot. */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable String id, RedirectAttributes flash) {
        bookingService.cancelByAdmin(id);
        flash.addFlashAttribute("successMessage",
                "Booking cancelled and the time slot released.");
        return "redirect:/admin/bookings/" + id;
    }
}
