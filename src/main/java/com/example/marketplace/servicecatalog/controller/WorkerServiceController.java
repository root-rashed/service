package com.example.marketplace.servicecatalog.controller;

import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.common.enums.PriceType;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.security.AppUserDetails;
import com.example.marketplace.servicecatalog.dto.ServiceListingForm;
import com.example.marketplace.servicecatalog.service.ServiceListingService;
import com.example.marketplace.worker.service.WorkerProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Worker's "My services" pages. Secured by SecurityConfig (/worker/** requires WORKER).
 * The worker id always comes from the authenticated principal; the service id from the URL is
 * only ever resolved together with that worker id.
 */
@Controller
@RequestMapping("/worker/services")
public class WorkerServiceController {

    private static final String FORM_VIEW = "worker/services/form";

    private final ServiceListingService serviceListingService;
    private final CategoryService categoryService;
    private final WorkerProfileService workerProfileService;

    public WorkerServiceController(ServiceListingService serviceListingService,
                                   CategoryService categoryService,
                                   WorkerProfileService workerProfileService) {
        this.serviceListingService = serviceListingService;
        this.categoryService = categoryService;
        this.workerProfileService = workerProfileService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("services", serviceListingService.listForWorker(user.getId()));
        model.addAttribute("profile", workerProfileService.getOwnProfile(user.getId()).orElse(null));
        return "worker/services/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AppUserDetails user,
                          Model model,
                          RedirectAttributes redirect) {
        if (!serviceListingService.hasProfile(user.getId())) {
            return profileRequired(redirect);
        }
        model.addAttribute("serviceForm", new ServiceListingForm());
        addFormContext(model, "Add a service", "/worker/services", "Publish service");
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails user,
                         @Valid @ModelAttribute("serviceForm") ServiceListingForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirect) {
        if (!serviceListingService.hasProfile(user.getId())) {
            return profileRequired(redirect);
        }
        if (!result.hasErrors()) {
            try {
                serviceListingService.create(user.getId(), form);
                redirect.addFlashAttribute("successMessage", "Service published.");
                return "redirect:/worker/services";
            } catch (ValidationException ex) {
                applyErrors(result, ex);
            }
        }
        addFormContext(model, "Add a service", "/worker/services", "Publish service");
        return FORM_VIEW;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails user,
                           @PathVariable String id,
                           Model model) {
        model.addAttribute("serviceForm", serviceListingService.getFormForEdit(user.getId(), id));
        addFormContext(model, "Edit service", "/worker/services/" + id, "Save changes");
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserDetails user,
                         @PathVariable String id,
                         @Valid @ModelAttribute("serviceForm") ServiceListingForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                serviceListingService.update(user.getId(), id, form);
                redirect.addFlashAttribute("successMessage", "Service updated.");
                return "redirect:/worker/services";
            } catch (ValidationException ex) {
                applyErrors(result, ex);
            }
        }
        addFormContext(model, "Edit service", "/worker/services/" + id, "Save changes");
        return FORM_VIEW;
    }

    @PostMapping("/{id}/activate")
    public String activate(@AuthenticationPrincipal AppUserDetails user,
                           @PathVariable String id,
                           RedirectAttributes redirect) {
        serviceListingService.setActive(user.getId(), id, true);
        redirect.addFlashAttribute("successMessage", "Service is now active.");
        return "redirect:/worker/services";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@AuthenticationPrincipal AppUserDetails user,
                             @PathVariable String id,
                             RedirectAttributes redirect) {
        serviceListingService.setActive(user.getId(), id, false);
        redirect.addFlashAttribute("successMessage", "Service paused. Customers can no longer book it.");
        return "redirect:/worker/services";
    }

    // ---------- Helpers ----------

    private String profileRequired(RedirectAttributes redirect) {
        redirect.addFlashAttribute("infoMessage", "Create your professional profile before adding services.");
        return "redirect:/worker/profile";
    }

    private void addFormContext(Model model, String pageTitle, String formAction, String submitLabel) {
        model.addAttribute("categories", categoryService.getActiveCategories());
        model.addAttribute("priceTypes", PriceType.values());
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("formAction", formAction);
        model.addAttribute("submitLabel", submitLabel);
    }

    private void applyErrors(BindingResult result, ValidationException ex) {
        if (ex.getFieldErrors().isEmpty()) {
            result.reject("service.invalid", ex.getMessage());
            return;
        }
        ex.getFieldErrors().forEach((field, message) -> result.rejectValue(field, "service.invalid", message));
    }
}
