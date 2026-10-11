package com.example.marketplace.schedule.controller;

import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.schedule.dto.BlockedDateForm;
import com.example.marketplace.schedule.dto.ScheduleForm;
import com.example.marketplace.schedule.service.WorkerScheduleService;
import com.example.marketplace.security.AppUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The worker's own schedule page. Secured by SecurityConfig (/worker/** requires WORKER);
 * the worker id always comes from the authenticated principal.
 */
@Controller
@RequestMapping("/worker/schedule")
public class WorkerScheduleController {

    private static final String VIEW = "worker/schedule";

    private final WorkerScheduleService scheduleService;

    public WorkerScheduleController(WorkerScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("scheduleForm", scheduleService.getForm(user.getId()));
        addCommon(user, model);
        return VIEW;
    }

    @PostMapping
    public String saveHours(@AuthenticationPrincipal AppUserDetails user,
                            @Valid @ModelAttribute("scheduleForm") ScheduleForm form,
                            BindingResult result,
                            Model model,
                            RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                scheduleService.saveWeeklyHours(user.getId(), form);
                redirect.addFlashAttribute("successMessage", "Working hours saved.");
                return "redirect:/worker/schedule";
            } catch (ValidationException ex) {
                if (ex.getFieldErrors().isEmpty()) {
                    result.reject("schedule.invalid", ex.getMessage());
                } else {
                    ex.getFieldErrors().forEach((field, message) -> result.rejectValue(field, "schedule.invalid", message));
                }
            }
        }
        addCommon(user, model);
        return VIEW;
    }

    @PostMapping("/blocked")
    public String block(@AuthenticationPrincipal AppUserDetails user,
                        @Valid @ModelAttribute("blockedForm") BlockedDateForm form,
                        BindingResult result,
                        RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "Please choose a valid start date.");
            return "redirect:/worker/schedule";
        }
        try {
            scheduleService.blockDates(user.getId(), form);
            redirect.addFlashAttribute("successMessage", "Dates blocked. Customers can't book you on those days.");
        } catch (ValidationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/worker/schedule";
    }

    @PostMapping("/blocked/remove")
    public String unblock(@AuthenticationPrincipal AppUserDetails user,
                          @RequestParam String date,
                          RedirectAttributes redirect) {
        scheduleService.unblockDate(user.getId(), date);
        redirect.addFlashAttribute("successMessage", "Date is available again.");
        return "redirect:/worker/schedule";
    }

    private void addCommon(AppUserDetails user, Model model) {
        model.addAttribute("blockedDates", scheduleService.getUpcomingBlockedDates(user.getId()));
        if (!model.containsAttribute("blockedForm")) {
            model.addAttribute("blockedForm", new BlockedDateForm());
        }
    }
}
