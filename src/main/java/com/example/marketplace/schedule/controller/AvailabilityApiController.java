package com.example.marketplace.schedule.controller;

import com.example.marketplace.common.response.ApiResponse;
import com.example.marketplace.schedule.dto.TimeSlot;
import com.example.marketplace.schedule.service.AvailabilityService;
import com.example.marketplace.servicecatalog.dto.ServiceDetailView;
import com.example.marketplace.servicecatalog.service.PublicServiceQueryService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Free time slots of a public service for one date, computed live (never cached).
 * Requires a logged-in user (anything under /api/** that is not listed as public).
 * The booking step will call AvailabilityService again before saving, so this is only a hint for the UI.
 */
@RestController
@RequestMapping("/api/services")
public class AvailabilityApiController {

    private final PublicServiceQueryService publicServiceQueryService;
    private final AvailabilityService availabilityService;

    public AvailabilityApiController(PublicServiceQueryService publicServiceQueryService,
                                     AvailabilityService availabilityService) {
        this.publicServiceQueryService = publicServiceQueryService;
        this.availabilityService = availabilityService;
    }

    @GetMapping("/{slug}/slots")
    public ApiResponse<List<TimeSlot>> slots(@PathVariable String slug,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ServiceDetailView view = publicServiceQueryService.getDetail(slug);
        List<TimeSlot> slots = availabilityService.getAvailableSlots(
                view.getService().getWorkerId(), view.getService().getDurationMinutes(), date);
        return ApiResponse.ok(slots);
    }
}
