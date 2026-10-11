package com.example.marketplace.servicecatalog.controller;

import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.security.AppUserDetails;
import com.example.marketplace.servicecatalog.service.ServiceImageService;
import com.example.marketplace.servicecatalog.service.ServiceListingService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Photo management for one of the logged-in worker's services.
 * Secured by SecurityConfig (/worker/** requires WORKER); ownership is enforced in the services.
 */
@Controller
@RequestMapping("/worker/services/{id}/images")
public class WorkerServiceImageController {

    private final ServiceListingService serviceListingService;
    private final ServiceImageService serviceImageService;

    public WorkerServiceImageController(ServiceListingService serviceListingService,
                                        ServiceImageService serviceImageService) {
        this.serviceListingService = serviceListingService;
        this.serviceImageService = serviceImageService;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AppUserDetails user, @PathVariable String id, Model model) {
        model.addAttribute("service", serviceListingService.getOwned(user.getId(), id));
        model.addAttribute("maxImages", ServiceImageService.MAX_IMAGES);
        return "worker/services/images";
    }

    @PostMapping
    public String upload(@AuthenticationPrincipal AppUserDetails user,
                         @PathVariable String id,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         RedirectAttributes redirect) {
        try {
            serviceImageService.upload(user.getId(), id, files);
            redirect.addFlashAttribute("successMessage", "Photos uploaded.");
        } catch (ValidationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return backTo(id);
    }

    @PostMapping("/{imageId}/primary")
    public String makePrimary(@AuthenticationPrincipal AppUserDetails user,
                              @PathVariable String id,
                              @PathVariable String imageId,
                              RedirectAttributes redirect) {
        serviceImageService.makePrimary(user.getId(), id, imageId);
        redirect.addFlashAttribute("successMessage", "Main photo updated.");
        return backTo(id);
    }

    @PostMapping("/{imageId}/move")
    public String move(@AuthenticationPrincipal AppUserDetails user,
                       @PathVariable String id,
                       @PathVariable String imageId,
                       @RequestParam String direction) {
        serviceImageService.move(user.getId(), id, imageId, "up".equals(direction));
        return backTo(id);
    }

    @PostMapping("/{imageId}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails user,
                         @PathVariable String id,
                         @PathVariable String imageId,
                         RedirectAttributes redirect) {
        serviceImageService.delete(user.getId(), id, imageId);
        redirect.addFlashAttribute("successMessage", "Photo deleted.");
        return backTo(id);
    }

    private String backTo(String serviceId) {
        return "redirect:/worker/services/" + serviceId + "/images";
    }
}
