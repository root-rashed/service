package com.example.marketplace.user.controller;

import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.security.AppUserDetails;
import com.example.marketplace.security.SessionPrincipalRefresher;
import com.example.marketplace.user.dto.CustomerDashboardSummary;
import com.example.marketplace.user.dto.ProfileUpdateForm;
import com.example.marketplace.user.service.AccountProfileService;
import com.example.marketplace.user.service.CustomerDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Customer area: dashboard shell and own profile. Secured by SecurityConfig (/customer/** requires CUSTOMER).
 * The user id always comes from the authenticated principal.
 */
@Controller
@RequestMapping("/customer")
public class CustomerController {

    private static final String PROFILE_REDIRECT = "redirect:/customer/profile";

    private final AccountProfileService accountProfileService;
    private final SessionPrincipalRefresher principalRefresher;
    private final CustomerDashboardService customerDashboardService;

    public CustomerController(AccountProfileService accountProfileService,
                              SessionPrincipalRefresher principalRefresher,
                              CustomerDashboardService customerDashboardService) {
        this.accountProfileService = accountProfileService;
        this.principalRefresher = principalRefresher;
        this.customerDashboardService = customerDashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserDetails user, Model model) {
        CustomerDashboardSummary summary = customerDashboardService.getDashboard(user.getId());
        model.addAttribute("account", summary.getAccount());
        model.addAttribute("bookings", summary.getBookings());
        model.addAttribute("savedServices", summary.getSavedServices());
        model.addAttribute("recentReviews", summary.getRecentReviews());
        model.addAttribute("notifications", summary.getNotifications());
        return "customer/dashboard";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("profileForm", accountProfileService.getForm(user.getId()));
        model.addAttribute("account", accountProfileService.getProfile(user.getId()));
        return "customer/profile";
    }

    @PostMapping("/profile")
    public String update(@AuthenticationPrincipal AppUserDetails user,
                         @Valid @ModelAttribute("profileForm") ProfileUpdateForm form,
                         BindingResult result,
                         Model model,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                accountProfileService.updateProfile(user.getId(), form);
                principalRefresher.refresh(user.getId(), request, response);
                redirect.addFlashAttribute("successMessage", "Profile updated.");
                return PROFILE_REDIRECT;
            } catch (ValidationException ex) {
                if (ex.getFieldErrors().isEmpty()) {
                    result.reject("profile.invalid", ex.getMessage());
                } else {
                    ex.getFieldErrors().forEach((field, message) -> result.rejectValue(field, "profile.invalid", message));
                }
            }
        }
        model.addAttribute("account", accountProfileService.getProfile(user.getId()));
        return "customer/profile";
    }

    @PostMapping("/profile/photo")
    public String uploadPhoto(@AuthenticationPrincipal AppUserDetails user,
                              @RequestParam("photo") MultipartFile photo,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              RedirectAttributes redirect) {
        try {
            accountProfileService.uploadPhoto(user.getId(), photo);
            principalRefresher.refresh(user.getId(), request, response);
            redirect.addFlashAttribute("successMessage", "Profile photo updated.");
        } catch (ValidationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return PROFILE_REDIRECT;
    }

    @PostMapping("/profile/photo/remove")
    public String removePhoto(@AuthenticationPrincipal AppUserDetails user,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              RedirectAttributes redirect) {
        accountProfileService.removePhoto(user.getId());
        principalRefresher.refresh(user.getId(), request, response);
        redirect.addFlashAttribute("successMessage", "Profile photo removed.");
        return PROFILE_REDIRECT;
    }
}
