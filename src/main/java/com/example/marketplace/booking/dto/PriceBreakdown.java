package com.example.marketplace.booking.dto;

import java.math.BigDecimal;

/** The amounts stored on a booking. All values have 2 decimal places. */
public record PriceBreakdown(BigDecimal servicePrice, BigDecimal platformFee, BigDecimal totalAmount) {
}
