package com.example.marketplace.worker.controller;

import com.example.marketplace.security.AppUserDetails;
import com.example.marketplace.worker.dto.WorkerDashboardSummary;
import com.example.marketplace.worker.service.WorkerDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The professional's landing page: what needs answering, what is coming up and what they have
 * earned. Secured by SecurityConfig ({@code /worker/**} requires the WORKER role), and the user id
 * always comes from the authenticated principal, never from the request.
 */
@Controller
@RequestMapping("/worker/dashboard")
public class WorkerDashboardController {

    private final WorkerDashboardService workerDashboardService;

    public WorkerDashboardController(WorkerDashboardService workerDashboardService) {
        this.workerDashboardService = workerDashboardService;
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal AppUserDetails user, Model model) {
        WorkerDashboardSummary summary = workerDashboardService.getDashboard(user.getId());
        model.addAttribute("summary", summary);
        model.addAttribute("profile", summary.getProfile());
        model.addAttribute("earnings", summary.getEarnings());
        model.addAttribute("activeNav", "worker-dashboard");
        return "worker/dashboard";
    }
}