package com.example.marketplace.booking.service;

import com.example.marketplace.booking.dto.PriceBreakdown;
import com.example.marketplace.common.enums.PriceType;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure price maths (no Spring, easy to test).
 * - FIXED and STARTING_FROM: the listed price is charged as is.
 * - HOURLY: the listed price is per hour and is multiplied by the booked duration.
 * The platform fee is a percentage of the service price. Everything is rounded half-up to 2 places.
 */
public final class PriceCalculator {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PriceCalculator() {
    }

    public static PriceBreakdown calculate(PriceType priceType, BigDecimal listedPrice,
                                           int durationMinutes, BigDecimal feePercent) {
        BigDecimal servicePrice = priceType == PriceType.HOURLY
                ? listedPrice.multiply(BigDecimal.valueOf(durationMinutes)).divide(SIXTY, 2, RoundingMode.HALF_UP)
                : listedPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformFee = servicePrice.multiply(feePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new PriceBreakdown(servicePrice, platformFee, servicePrice.add(platformFee));
    }
}
