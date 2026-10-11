package com.example.marketplace.worker.controller;

import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.security.AppUserDetails;
import com.example.marketplace.security.SessionPrincipalRefresher;
import com.example.marketplace.user.service.AccountProfileService;
import com.example.marketplace.worker.dto.WorkerProfileForm;
import com.example.marketplace.worker.service.WorkerProfileService;
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
 * Worker's own profile page. Secured by SecurityConfig (/worker/** requires WORKER).
 * The user id always comes from the authenticated principal, never from the request.
 */
@Controller
@RequestMapping("/worker/profile")
public class WorkerProfileController {

    private final WorkerProfileService workerProfileService;
    private final AccountProfileService accountProfileService;
    private final SessionPrincipalRefresher principalRefresher;

    public WorkerProfileController(WorkerProfileService workerProfileService,
                                   AccountProfileService accountProfileService,
                                   SessionPrincipalRefresher principalRefresher) {
        this.workerProfileService = workerProfileService;
        this.accountProfileService = accountProfileService;
        this.principalRefresher = principalRefresher;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("workerProfileForm", workerProfileService.getFormForUser(user.getId()));
        addProfile(user, model);
        return "worker/profile-form";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserDetails user,
                       @Valid @ModelAttribute("workerProfileForm") WorkerProfileForm form,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                workerProfileService.saveProfile(user.getId(), user.getDisplayName(), form);
                redirect.addFlashAttribute("successMessage", "Profile saved.");
                return "redirect:/worker/profile";
            } catch (ValidationException ex) {
                if (ex.getFieldErrors().isEmpty()) {
                    result.reject("profile.invalid", ex.getMessage());
                } else {
                    ex.getFieldErrors().forEach((field, message) ->
                            result.rejectValue(field, "profile.invalid", message));
                }
            }
        }
        addProfile(user, model);
        return "worker/profile-form";
    }

    @PostMapping("/photo")
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
        return "redirect:/worker/profile";
    }

    @PostMapping("/photo/remove")
    public String removePhoto(@AuthenticationPrincipal AppUserDetails user,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              RedirectAttributes redirect) {
        accountProfileService.removePhoto(user.getId());
        principalRefresher.refresh(user.getId(), request, response);
        redirect.addFlashAttribute("successMessage", "Profile photo removed.");
        return "redirect:/worker/profile";
    }

    private void addProfile(AppUserDetails user, Model model) {
        model.addAttribute("profile", workerProfileService.getOwnProfile(user.getId()).orElse(null));
        model.addAttribute("account", accountProfileService.getProfile(user.getId()));
    }
}
