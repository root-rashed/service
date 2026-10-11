package com.example.marketplace.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.stereotype.Component;

/**
 * Sends an authenticated user who lacks permission to the friendly 403 page.
 * Anonymous users are still redirected to the login form by ExceptionTranslationFilter
 * before this handler runs, so this only ever produces a 403 response.
 *
 * Nothing about the protected resource is echoed back to the client.
 */
@Component
public class AppAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AppAccessDeniedHandler.class);

    private final AccessDeniedHandlerImpl delegate = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            log.debug("Access denied on {} {} for {}", request.getMethod(),
                    request.getRequestURI(), auth.getName());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute("jakarta.servlet.error.status_code",
                    HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute("jakarta.servlet.error.request_uri", request.getRequestURI());
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }
        delegate.handle(request, response, accessDeniedException);
    }
}
