package com.example.marketplace.config;

import com.example.marketplace.security.AppAccessDeniedHandler;
import com.example.marketplace.security.AuthFailureHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Central, role-based security configuration.
 * Deny-by-default: any route not listed below requires authentication.
 * CSRF protection stays enabled; logout is POST-only.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ROUTES = {
            "/", "/error",
            "/services", "/services/**",
            "/categories", "/categories/**",
            "/workers", "/workers/**",
            "/search", "/search/**",
            "/about", "/contact", "/faq",
            "/chat", "/chat/**",
            "/login",
            "/register", "/register/**",
            "/forgot-password", "/forgot-password/**",
            "/reset-password", "/reset-password/**",
            "/verify-otp", "/verify-otp/**",
            "/resend-otp",
            "/css/**", "/js/**", "/images/**", "/uploads/public/**", "/favicon.ico",
            "/actuator/health", "/actuator/health/**"
    };

    private static final String[] CUSTOMER_ROUTES = {
            "/customer/**", "/booking/**", "/checkout/**",
            "/payment/**", "/review/**", "/favorites/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthFailureHandler authFailureHandler,
                                                   AppAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/worker/**").hasRole("WORKER")
                        .requestMatchers(CUSTOMER_ROUTES).hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(new com.example.marketplace.security.RoleBasedAuthenticationSuccessHandler())
                        .failureHandler(authFailureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));
        return http.build();
    }
}
