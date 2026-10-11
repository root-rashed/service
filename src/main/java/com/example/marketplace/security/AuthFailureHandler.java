package com.example.marketplace.security;

import com.example.marketplace.common.enums.UserStatus;
import com.example.marketplace.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Maps login failures to friendly login-page states:
 * - bad credentials      -> /login?error
 * - temporarily locked   -> /login?locked
 * - email not verified   -> /verify-otp?email=...&pending
 * - suspended account    -> /login?suspended
 */
@Component
public class AuthFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository userRepository;

    public AuthFailureHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String target = "/login?error";

        if (exception instanceof LockedException) {
            target = "/login?locked";
        } else if (exception instanceof DisabledException) {
            String raw = request.getParameter("email");
            String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            target = userRepository.findByEmail(email)
                    .filter(u -> u.getStatus() == UserStatus.PENDING_VERIFICATION)
                    .map(u -> "/verify-otp?pending&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8))
                    .orElse("/login?suspended");
        }

        response.sendRedirect(request.getContextPath() + target);
    }
}
