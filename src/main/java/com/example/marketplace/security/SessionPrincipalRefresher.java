package com.example.marketplace.security;

import com.example.marketplace.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the logged-in user's session principal from the database, so a changed name or photo
 * shows up in the header immediately instead of after the next login.
 */
@Component
public class SessionPrincipalRefresher {

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public SessionPrincipalRefresher(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void refresh(String userId, HttpServletRequest request, HttpServletResponse response) {
        userRepository.findById(userId).ifPresent(user -> {
            AppUserDetails details = new AppUserDetails(user);
            Authentication authentication = UsernamePasswordAuthenticationToken
                    .authenticated(details, null, details.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        });
    }
}
