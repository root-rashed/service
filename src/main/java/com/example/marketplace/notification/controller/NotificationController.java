package com.example.marketplace.notification.controller;

import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.notification.dto.NotificationDto;
import com.example.marketplace.notification.service.NotificationService;
import com.example.marketplace.security.AppUserDetails;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The signed-in user's own notification centre.
 * Any authenticated role may read it; every lookup and update is scoped to the principal's
 * user id, so a notification id from a URL can only ever mark that account's own message as read.
 */
@Controller
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal AppUserDetails user,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("notifications", notificationService.list(user.getId(), page));
        return "notification/list";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@AuthenticationPrincipal AppUserDetails user,
                           @PathVariable String id,
                           @RequestParam(required = false) String link,
                           RedirectAttributes redirect) {
        try {
            notificationService.markRead(user.getId(), id);
        } catch (ResourceNotFoundException ex) {
            redirect.addFlashAttribute("errorMessage", "That notification no longer exists.");
            return "redirect:/notifications";
        }
        String target = safeLink(link);
        return target == null ? "redirect:/notifications" : "redirect:" + target;
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead(@AuthenticationPrincipal AppUserDetails user, RedirectAttributes redirect) {
        notificationService.markAllRead(user.getId());
        redirect.addFlashAttribute("successMessage", "All notifications marked as read.");
        return "redirect:/notifications";
    }

    /** Unread count for the header bell; polled by the navbar. */
    @GetMapping("/api/notifications/summary")
    public Map<String, Object> summary(@AuthenticationPrincipal AppUserDetails user) {
        List<NotificationDto> unread = notificationService.recentUnread(user.getId());
        return Map.of("unreadCount", notificationService.unreadCount(user.getId()), "recent", unread);
    }

    /** Only in-app links are followed; anything else returns the user to their list. */
    private String safeLink(String link) {
        return link != null && link.startsWith("/") && !link.startsWith("//") ? link : null;
    }
}
