package com.example.marketplace.security;

import com.example.marketplace.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads accounts by email for Spring Security. The username is the email address. */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .map(AppUserDetails::new)
                // Spring converts this to a generic "bad credentials" error (no user enumeration).
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
