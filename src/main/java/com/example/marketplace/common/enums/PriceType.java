package com.example.marketplace.common.enums;

public enum PriceType {
    FIXED("Fixed price", "per service"),
    STARTING_FROM("Starting from", "starting price"),
    HOURLY("Hourly", "per hour");

    private final String label;
    private final String unitLabel;

    PriceType(String label, String unitLabel) {
        this.label = label;
        this.unitLabel = unitLabel;
    }

    public String getLabel() {
        return label;
    }

    /** Short suffix shown next to the price on cards, e.g. "per hour". */
    public String getUnitLabel() {
        return unitLabel;
    }
}
