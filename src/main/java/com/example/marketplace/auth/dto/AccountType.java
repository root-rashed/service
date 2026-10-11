package com.example.marketplace.auth.dto;

import com.example.marketplace.common.enums.Role;

/**
 * Account types a visitor may self-register as.
 * ADMIN is intentionally absent so it can never be requested from the browser.
 */
public enum AccountType {
    CUSTOMER(Role.CUSTOMER),
    WORKER(Role.WORKER);

    private final Role role;

    AccountType(Role role) {
        this.role = role;
    }

    public Role toRole() {
        return role;
    }
}
