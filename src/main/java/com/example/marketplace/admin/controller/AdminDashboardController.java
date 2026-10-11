package com.example.marketplace.admin.controller;

import com.example.marketplace.admin.dto.AdminWorkerDto;
import com.example.marketplace.admin.service.AdminBookingService;
import com.example.marketplace.admin.service.AdminDashboardService;
import com.example.marketplace.admin.service.AdminReportService;
import com.example.marketplace.admin.service.AdminSettingsService;
import com.example.marketplace.admin.service.AdminWorkerService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin landing page, reports and the read-only settings view.
 *
 * Secured by SecurityConfig ({@code /admin/**} requires the ADMIN role); nothing here does
 * authorization of its own, and no business rule lives in this class.
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    /** How many rows of each queue the dashboard previews. */
    private static final int PREVIEW_SIZE = 5;

    private final AdminDashboardService dashboardService;
    private final AdminWorkerService adminWorkerService;
    private final AdminBookingService adminBookingService;
    private final AdminReportService reportService;
    private final AdminSettingsService settingsService;

    public AdminDashboardController(AdminDashboardService dashboardService,
                                    AdminWorkerService adminWorkerService,
                                    AdminBookingService adminBookingService,
                                    AdminReportService reportService,
                                    AdminSettingsService settingsService) {
        this.dashboardService = dashboardService;
        this.adminWorkerService = adminWorkerService;
        this.adminBookingService = adminBookingService;
        this.reportService = reportService;
        this.settingsService = settingsService;
    }

    /** {@code /admin} is just a shorter way into the dashboard. */
    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        Page<AdminWorkerDto> pending = adminWorkerService.getVerificationQueue(0);
        model.addAttribute("summary", dashboardService.getSummary());
        model.addAttribute("openBookings", dashboardService.getOpenBookings());
        model.addAttribute("recentBookings", dashboardService.getRecentBookings());
        model.addAttribute("pendingWorkers", pending.getContent());
        model.addAttribute("pendingCount", pending.getTotalElements());
        model.addAttribute("unpaidBookings", dashboardService.getUnpaidCompletedBookings());
        model.addAttribute("unpaidCount", adminBookingService.getUnpaid(0).getTotalElements());
        model.addAttribute("newUsers", dashboardService.getNewestUsers());
        model.addAttribute("activeSection", "dashboard");
        return "admin/dashboard";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("report", reportService.getReport());
        model.addAttribute("activeSection", "reports");
        return "admin/reports";
    }

    /** Read-only view of the runtime settings. */
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("settings", settingsService.getSettings());
        model.addAttribute("activeSection", "settings");
        return "admin/settings";
    }
}
