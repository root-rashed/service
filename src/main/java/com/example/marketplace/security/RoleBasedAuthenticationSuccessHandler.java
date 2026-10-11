package com.example.marketplace.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

/**
 * Redirects authenticated users to role-specific dashboard pages after successful login.
 * - ADMIN → /admin/dashboard
 * - WORKER → /worker/dashboard
 * - CUSTOMER → /customer/dashboard
 * - Fallback → /
 */
@Component
public class RoleBasedAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private static final Logger logger = LoggerFactory.getLogger(RoleBasedAuthenticationSuccessHandler.class);

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        String redirectUrl = determineTargetUrl(authentication);

        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect to " + redirectUrl);
            return;
        }

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    protected String determineTargetUrl(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        for (GrantedAuthority authority : authorities) {
            String authorityName = authority.getAuthority();
            if ("ROLE_ADMIN".equals(authorityName)) {
                return "/admin/dashboard";
            }
            if ("ROLE_WORKER".equals(authorityName)) {
                return "/worker/dashboard";
            }
            if ("ROLE_CUSTOMER".equals(authorityName)) {
                return "/customer/dashboard";
            }
        }

        // Fallback to homepage
        return "/";
    }
}
