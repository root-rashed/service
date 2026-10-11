package com.example.marketplace.admin.controller;

import com.example.marketplace.admin.dto.AdminServiceDto;
import com.example.marketplace.admin.service.AdminCatalogService;
import com.example.marketplace.admin.web.AdminFilters;
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
 * Service-listing moderation: browse every listing and take an individual one out of, or back
 * into, the public catalog.
 */
@Controller
@RequestMapping("/admin/services")
public class AdminServiceController {

    private final AdminCatalogService adminCatalogService;

    public AdminServiceController(AdminCatalogService adminCatalogService) {
        this.adminCatalogService = adminCatalogService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<AdminServiceDto> services = adminCatalogService.search(status, q, page);
        model.addAttribute("services", services);
        model.addAttribute("counts", adminCatalogService.countsByStatus());
        model.addAttribute("statuses", adminCatalogService.statuses());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("filterQuery", AdminFilters.query("status", status, "q", q));
        model.addAttribute("activeSection", "services");
        return "admin/services";
    }

    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable String id, RedirectAttributes flash) {
        adminCatalogService.suspend(id);
        flash.addFlashAttribute("successMessage", "Service suspended and removed from the public catalog.");
        return "redirect:/admin/services";
    }

    @PostMapping("/{id}/reinstate")
    public String reinstate(@PathVariable String id, RedirectAttributes flash) {
        adminCatalogService.reinstate(id);
        flash.addFlashAttribute("successMessage", "Service is live again.");
        return "redirect:/admin/services";
    }
}
