package com.example.marketplace.security;

import com.example.marketplace.common.enums.Role;
import com.example.marketplace.media.MediaUrls;
import com.example.marketplace.user.model.User;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Lightweight, serializable principal stored in the HTTP session.
 * Holds only what security decisions and the UI header need.
 */
public class AppUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final Instant lockedUntil;
    private final String profileImageUrl;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.getStatus() != null && user.getStatus().canLogin();
        this.lockedUntil = user.getLockedUntil();
        this.profileImageUrl = MediaUrls.publicUrl(user.getProfileImage());
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Public URL of the profile photo, or null if the user has none. */
    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(Instant.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
