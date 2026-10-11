package com.example.marketplace.admin.controller;

import com.example.marketplace.admin.dto.AdminReviewDto;
import com.example.marketplace.admin.service.AdminReviewService;
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
 * Review moderation: hide a review that breaks the rules, or publish it again.
 *
 * Nothing here deletes a review or recalculates a rating — {@link AdminReviewService} only flips
 * the visibility flag and clears the caches that displayed it.
 */
@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    public AdminReviewController(AdminReviewService adminReviewService) {
        this.adminReviewService = adminReviewService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String visibility,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<AdminReviewDto> reviews = adminReviewService.search(visibility, q, page);
        model.addAttribute("reviews", reviews);
        model.addAttribute("visibleCount", adminReviewService.countVisible());
        model.addAttribute("hiddenCount", adminReviewService.countHidden());
        model.addAttribute("visibility", visibility == null ? "" : visibility);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("filterQuery", AdminFilters.query("visibility", visibility, "q", q));
        model.addAttribute("activeSection", "reviews");
        return "admin/reviews";
    }

    @PostMapping("/{id}/hide")
    public String hide(@PathVariable String id, RedirectAttributes flash) {
        adminReviewService.hide(id);
        flash.addFlashAttribute("successMessage", "Review hidden from the public pages.");
        return "redirect:/admin/reviews";
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable String id, RedirectAttributes flash) {
        adminReviewService.publish(id);
        flash.addFlashAttribute("successMessage", "Review published again.");
        return "redirect:/admin/reviews";
    }
}
