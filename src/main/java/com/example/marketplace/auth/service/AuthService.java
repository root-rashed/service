package com.example.marketplace.auth.service;

import com.example.marketplace.auth.dto.ForgotPasswordRequest;
import com.example.marketplace.auth.dto.RegisterRequest;
import com.example.marketplace.auth.dto.ResetPasswordRequest;
import com.example.marketplace.auth.dto.VerifyOtpRequest;
import com.example.marketplace.common.enums.OtpPurpose;
import com.example.marketplace.common.enums.UserStatus;
import com.example.marketplace.common.exception.OtpException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.otp.service.OtpService;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.repository.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Registration, email verification and password reset.
 * Roles come only from the server-side AccountType whitelist, passwords are only stored
 * as BCrypt hashes, and recovery flows do not reveal whether an email is registered.
 */
@Service
public class AuthService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
    }

    /** Creates (or refreshes) an unverified account and emails a verification code. */
    public void register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ValidationException("Registration failed.",
                    Map.of("confirmPassword", "Passwords do not match."));
        }

        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());

        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new ValidationException("Registration failed.",
                    Map.of("email", "This email is already registered."));
        }
        if (user == null) {
            user = User.builder().email(email).status(UserStatus.PENDING_VERIFICATION).build();
        }

        // An unverified record is simply overwritten, so the real owner of the email can always re-register.
        user.setName(request.getName().trim());
        user.setPhone(phone);
        user.setRole(request.getAccountType().toRole());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        try {
            userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new ValidationException("Registration failed.",
                    Map.of("email", "This email is already registered."));
        }

        otpService.issueAndSend(email, user.getName(), OtpPurpose.REGISTRATION);
    }

    /** Verifies the registration code and activates the account. */
    public void verifyRegistration(VerifyOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        otpService.verify(email, OtpPurpose.REGISTRATION, request.getCode());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new OtpException("Invalid or expired code."));
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }
    }

    /** Resends the registration code. Silent for unknown or already-verified emails. */
    public void resendRegistrationOtp(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        userRepository.findByEmail(email)
                .filter(u -> u.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(u -> otpService.issueAndSend(email, u.getName(), OtpPurpose.REGISTRATION));
    }

    /**
     * Starts a password reset. Always completes silently so callers can show the same
     * "if an account exists, we sent a code" message (no account enumeration).
     */
    public void requestPasswordReset(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        userRepository.findByEmail(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .ifPresent(u -> {
                    try {
                        otpService.issueAndSend(email, u.getName(), OtpPurpose.PASSWORD_RESET);
                    } catch (OtpException ignored) {
                        // Cooldown / hourly cap: swallowed so the response cannot reveal the account exists.
                    }
                });
    }

    /** Verifies the reset code and sets the new password. Also clears any login lock. */
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ValidationException("Password reset failed.",
                    Map.of("confirmPassword", "Passwords do not match."));
        }

        String email = normalizeEmail(request.getEmail());
        otpService.verify(email, OtpPurpose.PASSWORD_RESET, request.getCode());

        User user = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new OtpException("Invalid or expired code."));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        String cleaned = phone == null ? "" : phone.replaceAll("[\\s\\-()]", "");
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new ValidationException("Registration failed.",
                    Map.of("phone", "Please enter a valid phone number."));
        }
        return cleaned;
    }
}
