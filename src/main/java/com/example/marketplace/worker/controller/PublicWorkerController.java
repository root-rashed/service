package com.example.marketplace.worker.controller;

import com.example.marketplace.review.service.ReviewService;
import com.example.marketplace.worker.dto.WorkerProfileDto;
import com.example.marketplace.worker.service.WorkerProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Public worker directory and profile pages (approved professionals only). */
@Controller
public class PublicWorkerController {

    private final WorkerProfileService workerProfileService;
    private final ReviewService reviewService;

    public PublicWorkerController(WorkerProfileService workerProfileService, ReviewService reviewService) {
        this.workerProfileService = workerProfileService;
        this.reviewService = reviewService;
    }

    @GetMapping("/workers")
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("workers", workerProfileService.getPublicProfiles(page));
        model.addAttribute("activeNav", "professionals");
        return "workers/list";
    }

    @GetMapping("/workers/{slug}")
    public String detail(@PathVariable String slug, Model model) {
        WorkerProfileDto worker = workerProfileService.getPublicBySlug(slug);
        model.addAttribute("worker", worker);
        model.addAttribute("reviews", reviewService.getLatestForWorker(worker.getUserId(), 10));
        model.addAttribute("activeNav", "professionals");
        return "workers/detail";
    }
}
