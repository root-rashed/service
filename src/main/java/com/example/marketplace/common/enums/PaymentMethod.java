package com.example.marketplace.common.enums;

/** Cash is the only supported payment method. */
public enum PaymentMethod {
    CASH("Cash");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
