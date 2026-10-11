package com.example.marketplace.admin.controller;

import com.example.marketplace.admin.dto.AdminWorkerDto;
import com.example.marketplace.admin.dto.ModerationForm;
import com.example.marketplace.admin.service.AdminCatalogService;
import com.example.marketplace.admin.service.AdminWorkerService;
import com.example.marketplace.admin.web.AdminFilters;
import com.example.marketplace.common.enums.WorkerVerificationStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Professional-profile moderation: the verification queue, the full directory, and the four
 * decisions an administrator can make on a profile.
 *
 * Each decision posts through {@link ModerationForm} so the administrator can leave a short note;
 * the note travels to the professional as a notification and is never rendered as markup.
 */
@Controller
@RequestMapping("/admin/workers")
public class AdminWorkerController {

    private final AdminWorkerService adminWorkerService;
    private final AdminCatalogService adminCatalogService;

    public AdminWorkerController(AdminWorkerService adminWorkerService,
                                 AdminCatalogService adminCatalogService) {
        this.adminWorkerService = adminWorkerService;
        this.adminCatalogService = adminCatalogService;
    }

    /** The directory of every professional, with optional status and keyword filters. */
    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<AdminWorkerDto> workers = adminWorkerService.search(status, q, page);
        model.addAttribute("workers", workers);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("statuses", WorkerVerificationStatus.values());
        model.addAttribute("filterQuery", AdminFilters.query("status", status, "q", q));
        model.addAttribute("activeSection", "workers");
        return "admin/workers";
    }

    /** Profiles waiting for a decision. Reachable from the dashboard's moderation queue card. */
    @GetMapping("/queue")
    public String queue(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<AdminWorkerDto> pending = adminWorkerService.getVerificationQueue(page);
        model.addAttribute("workers", pending);
        model.addAttribute("status", WorkerVerificationStatus.PENDING.name());
        model.addAttribute("q", "");
        model.addAttribute("statuses", WorkerVerificationStatus.values());
        model.addAttribute("filterQuery", "");
        model.addAttribute("activeSection", "workers");
        return "admin/workers";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        AdminWorkerDto worker = adminWorkerService.get(id);
        model.addAttribute("worker", worker);
        model.addAttribute("services", adminCatalogService.getByWorker(worker.getUserId()));
        model.addAttribute("moderationForm", new ModerationForm());
        model.addAttribute("activeSection", "workers");
        return "admin/worker-detail";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable String id,
                          @Valid @ModelAttribute("moderationForm") ModerationForm form,
                          RedirectAttributes flash) {
        return decided(adminWorkerService.approve(id, form.getNote()),
                "Profile approved. The professional's listings are now live.", flash);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable String id,
                         @Valid @ModelAttribute("moderationForm") ModerationForm form,
                         RedirectAttributes flash) {
        return decided(adminWorkerService.reject(id, form.getNote()),
                "Profile marked as not approved.", flash);
    }

    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable String id,
                          @Valid @ModelAttribute("moderationForm") ModerationForm form,
                          RedirectAttributes flash) {
        return decided(adminWorkerService.suspend(id, form.getNote()),
                "Profile suspended. Its listings have been hidden from the catalog.", flash);
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable String id,
                          @Valid @ModelAttribute("moderationForm") ModerationForm form,
                          RedirectAttributes flash) {
        return decided(adminWorkerService.restore(id, form.getNote()),
                "Profile is back in the review queue.", flash);
    }

    /** Always returns to the profile that was just decided on. */
    private String decided(AdminWorkerDto worker, String message, RedirectAttributes flash) {
        flash.addFlashAttribute("successMessage", message);
        return "redirect:/admin/workers/" + worker.getId();
    }
}
