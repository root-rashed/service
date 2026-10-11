package com.example.marketplace.review.controller;

import com.example.marketplace.review.service.ReviewService;
import com.example.marketplace.security.AppUserDetails;
import com.example.marketplace.worker.service.WorkerProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The reviews a worker has received. /worker/** requires WORKER (SecurityConfig). */
@Controller
public class WorkerReviewController {

    private final ReviewService reviewService;
    private final WorkerProfileService workerProfileService;

    public WorkerReviewController(ReviewService reviewService, WorkerProfileService workerProfileService) {
        this.reviewService = reviewService;
        this.workerProfileService = workerProfileService;
    }

    @GetMapping("/worker/reviews")
    public String list(@AuthenticationPrincipal AppUserDetails user,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("reviews", reviewService.getForWorker(user.getId(), page));
        model.addAttribute("profile", workerProfileService.getOwnProfile(user.getId()).orElse(null));
        return "worker/reviews";
    }
}
