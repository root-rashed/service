package com.example.marketplace.servicecatalog.dto;

/** Sort options for service search. DISTANCE is only valid when a map position is supplied. */
public enum ServiceSort {
    RELEVANCE("Best match"),
    RATING("Top rated"),
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low"),
    NEWEST("Newest"),
    DISTANCE("Nearest");

    private final String label;

    ServiceSort(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
