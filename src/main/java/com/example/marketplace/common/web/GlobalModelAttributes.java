package com.example.marketplace.common.web;

import com.example.marketplace.common.enums.Role;
import com.example.marketplace.notification.service.NotificationService;
import com.example.marketplace.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Model attributes available to every controller-rendered page.
 *
 * Computed here (not with @bean expressions in templates) because Thymeleaf 3.1
 * restricts what expressions may do inside some attributes such as th:href.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final String baseUrl;
    private final NotificationService notificationService;

    /**
     * Notifications are optional here (ObjectProvider) so the advice can never prevent the
     * context from starting if the notification module is not present.
     */
    public GlobalModelAttributes(@Value("${app.base-url:}") String baseUrl,
                                 ObjectProvider<NotificationService> notificationService) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.notificationService = notificationService.getIfAvailable();
    }

    /** Absolute canonical URL of the current page (path only, query string excluded). */
    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(HttpServletRequest request) {
        return baseUrl + request.getRequestURI();
    }

    /**
     * Unread message count for the header bell. Always 0 for visitors, so templates can use it
     * unconditionally. Only counted for signed-in users, and never cached (it changes constantly).
     */
    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        if (notificationService == null) {
            return 0;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails user)) {
            return 0;
        }
        // Admin notifications are not used by the marketplace; keep their header clean.
        if (user.getRole() == Role.ADMIN) {
            return 0;
        }
        try {
            return notificationService.unreadCount(user.getId());
        } catch (RuntimeException ex) {
            return 0; // never let a header count break a page render
        }
    }
}
