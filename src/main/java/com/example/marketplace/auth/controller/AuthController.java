package com.example.marketplace.auth.controller;

import com.example.marketplace.auth.dto.AccountType;
import com.example.marketplace.auth.dto.ForgotPasswordRequest;
import com.example.marketplace.auth.dto.RegisterRequest;
import com.example.marketplace.auth.dto.ResetPasswordRequest;
import com.example.marketplace.auth.dto.VerifyOtpRequest;
import com.example.marketplace.auth.service.AuthService;
import com.example.marketplace.common.exception.OtpException;
import com.example.marketplace.common.exception.ValidationException;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Web layer for login, registration, email verification and password reset. No business logic here. */
@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ---------- Login (POST /login is handled by Spring Security) ----------

    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        return isSignedIn(authentication) ? "redirect:/" : "auth/login";
    }

    // ---------- Registration ----------

    @GetMapping("/register")
    public String registerPage(@RequestParam(required = false) String type,
                               Authentication authentication,
                               Model model) {
        if (isSignedIn(authentication)) {
            return "redirect:/";
        }
        RegisterRequest form = new RegisterRequest();
        if ("WORKER".equalsIgnoreCase(type)) {
            form.setAccountType(AccountType.WORKER);
        }
        model.addAttribute("registerRequest", form);
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest form,
                           BindingResult result,
                           RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "auth/register";
        }
        String email = normalize(form.getEmail());
        try {
            authService.register(form);
            redirect.addFlashAttribute("infoMessage", "We sent a 6-digit code to " + email + ".");
        } catch (ValidationException ex) {
            applyErrors(result, ex);
            return "auth/register";
        } catch (OtpException ex) {
            // Account exists; a code was sent very recently (cooldown), so go on to the verify step.
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        redirect.addAttribute("email", email);
        return "redirect:/verify-otp";
    }

    // ---------- Email verification ----------

    @GetMapping("/verify-otp")
    public String verifyPage(@RequestParam(required = false) String email, Model model) {
        if (email == null || email.isBlank()) {
            return "redirect:/register";
        }
        VerifyOtpRequest form = new VerifyOtpRequest();
        form.setEmail(email.trim());
        model.addAttribute("verifyOtpRequest", form);
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verify(@Valid @ModelAttribute("verifyOtpRequest") VerifyOtpRequest form,
                         BindingResult result,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "auth/verify-otp";
        }
        try {
            authService.verifyRegistration(form);
        } catch (OtpException ex) {
            result.rejectValue("code", "otp.invalid", ex.getMessage());
            return "auth/verify-otp";
        }
        redirect.addFlashAttribute("successMessage", "Email verified. You can log in now.");
        return "redirect:/login";
    }

    @PostMapping("/resend-otp")
    public String resend(@RequestParam String email, RedirectAttributes redirect) {
        try {
            authService.resendRegistrationOtp(email);
            redirect.addFlashAttribute("infoMessage", "If this account is waiting for verification, a new code is on its way.");
        } catch (OtpException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        redirect.addAttribute("email", normalize(email));
        return "redirect:/verify-otp";
    }

    // ---------- Forgot / reset password ----------

    @GetMapping("/forgot-password")
    public String forgotPage(Model model) {
        model.addAttribute("forgotPasswordRequest", new ForgotPasswordRequest());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgot(@Valid @ModelAttribute("forgotPasswordRequest") ForgotPasswordRequest form,
                         BindingResult result,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "auth/forgot-password";
        }
        authService.requestPasswordReset(form);
        String email = normalize(form.getEmail());
        redirect.addFlashAttribute("infoMessage",
                "If an account exists for " + email + ", we sent a 6-digit code to it.");
        redirect.addAttribute("email", email);
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String resetPage(@RequestParam(required = false) String email, Model model) {
        if (email == null || email.isBlank()) {
            return "redirect:/forgot-password";
        }
        ResetPasswordRequest form = new ResetPasswordRequest();
        form.setEmail(email.trim());
        model.addAttribute("resetPasswordRequest", form);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@Valid @ModelAttribute("resetPasswordRequest") ResetPasswordRequest form,
                        BindingResult result,
                        RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "auth/reset-password";
        }
        try {
            authService.resetPassword(form);
        } catch (ValidationException ex) {
            applyErrors(result, ex);
            return "auth/reset-password";
        } catch (OtpException ex) {
            result.rejectValue("code", "otp.invalid", ex.getMessage());
            return "auth/reset-password";
        }
        redirect.addFlashAttribute("successMessage", "Password updated. Log in with your new password.");
        return "redirect:/login";
    }

    // ---------- Helpers ----------

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void applyErrors(BindingResult result, ValidationException ex) {
        if (ex.getFieldErrors().isEmpty()) {
            result.reject("validation.failed", ex.getMessage());
            return;
        }
        ex.getFieldErrors().forEach((field, message) -> result.rejectValue(field, "validation.failed", message));
    }
}
