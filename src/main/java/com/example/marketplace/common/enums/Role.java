package com.example.marketplace.common.enums;

/** The three primary roles. Roles are always resolved server-side, never from the browser. */
public enum Role {
    ADMIN,
    CUSTOMER,
    WORKER;

    /** Spring Security authority string, e.g. ROLE_ADMIN. */
    public String authority() {
        return "ROLE_" + name();
    }
}
